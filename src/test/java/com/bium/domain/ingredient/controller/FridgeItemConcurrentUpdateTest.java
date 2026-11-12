package com.bium.domain.ingredient.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.ingredient.dto.FridgeItemUpdateRequest;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.ingredient.repository.FridgeItemRepository;
import com.bium.domain.ingredient.service.FridgeItemService;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

/**
 * 공유 냉장고에서 두 멤버가 같은 재료를 동시에 고치면 나중에 저장한 쪽이 실패해야 한다.
 * 트랜잭션 두 개가 필요해서 테스트 트랜잭션 없이 직접 연다.
 */
@SpringBootTest
class FridgeItemConcurrentUpdateTest {

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private EntityManager em;

	@Autowired
	private FridgeItemRepository fridgeItemRepository;

	@Autowired
	private FridgeItemService fridgeItemService;

	private TransactionTemplate otherMember;
	private Long userId;
	private Long fridgeId;
	private Long itemId;

	@BeforeEach
	void setUp() {
		transactionTemplate.executeWithoutResult(status -> {
			User user = em.merge(new User("concurrent-member", "password", "멤버"));
			Fridge fridge = em.merge(new Fridge("공유 냉장고"));
			em.persist(new FridgeMember(fridge, user, MemberRole.MEMBER));
			FridgeItem item = FridgeItem.builder()
				.fridge(fridge)
				.name("두부")
				.quantity(BigDecimal.ONE)
				.storage(StorageType.FRIDGE)
				.section(SectionType.CENTER)
				.sortOrder(0)
				.build();
			em.persist(item);
			userId = user.getId();
			fridgeId = fridge.getId();
			itemId = item.getId();
		});

		otherMember = new TransactionTemplate(transactionTemplate.getTransactionManager());
		otherMember.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	@Test
	void 먼저_다른_사람이_고친_재료를_저장하면_충돌로_막는다() {
		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			FridgeItem mine = fridgeItemRepository.findById(itemId).orElseThrow();
			// 내가 고치는 사이 다른 멤버가 먼저 고쳐서 저장한다
			otherMember.executeWithoutResult(otherStatus -> fridgeItemRepository.findById(itemId).orElseThrow()
				.update("다른 사람이 고친 두부", null, null));
			mine.update("내가 고친 두부", null, null);
		})).isInstanceOf(OptimisticLockingFailureException.class);
	}

	@Test
	void 바꾼_값이_없어도_저장이_끝날_때까지_다른_사람의_수정을_기다리게_한다() throws Exception {
		// 재료를 잠그고 읽으므로, 버전을 확인한 뒤 저장이 끝나기 전에 다른 멤버가 끼어들어 고칠 수 없다
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch checked = new CountDownLatch(1);
		CountDownLatch commit = new CountDownLatch(1);
		Future<?> mine = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
			fridgeItemService.update(userId, itemId, new FridgeItemUpdateRequest("두부", null, null, 0));
			checked.countDown();
			try {
				commit.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}));
		assertThat(checked.await(10, TimeUnit.SECONDS)).isTrue();

		Future<?> other = executor.submit(() -> otherMember.executeWithoutResult(otherStatus ->
			fridgeItemRepository.findById(itemId).orElseThrow().update("다른 사람이 고친 두부", null, null)));
		Thread.sleep(300);
		boolean otherFinishedFirst = other.isDone();
		commit.countDown();
		mine.get(10, TimeUnit.SECONDS);
		other.get(10, TimeUnit.SECONDS);
		executor.shutdown();

		assertThat(otherFinishedFirst).as("다른 멤버의 수정이 내 저장보다 먼저 끝났다").isFalse();
		// 다른 멤버의 수정은 내 저장 뒤에 들어가 사라지지 않는다
		FridgeItem saved = transactionTemplate.execute(status -> em.find(FridgeItem.class, itemId));
		assertThat(saved.getName()).isEqualTo("다른 사람이 고친 두부");
	}

	@AfterEach
	void tearDown() {
		transactionTemplate.executeWithoutResult(status -> {
			em.createQuery("delete from ActivityLog where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from FridgeItem where id = :id").setParameter("id", itemId).executeUpdate();
			em.createQuery("delete from FridgeMember where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from Fridge where id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from User where id = :id").setParameter("id", userId).executeUpdate();
		});
	}

}
