package com.bium.domain.ingredient.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

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
	void 바꾼_값이_없어도_그사이_다른_사람이_고쳤으면_충돌로_막는다() {
		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			fridgeItemService.update(userId, itemId, new FridgeItemUpdateRequest("두부", null, null, 0));
			// 버전 확인을 지난 뒤 저장이 끝나기 전에 다른 멤버가 고친다
			otherMember.executeWithoutResult(otherStatus -> fridgeItemRepository.findById(itemId).orElseThrow()
				.update("다른 사람이 고친 두부", null, null));
		})).isInstanceOf(OptimisticLockingFailureException.class);
	}

	@AfterEach
	void tearDown() {
		transactionTemplate.executeWithoutResult(status -> {
			em.createQuery("delete from FridgeItem where id = :id").setParameter("id", itemId).executeUpdate();
			em.createQuery("delete from FridgeMember where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from Fridge where id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from User where id = :id").setParameter("id", userId).executeUpdate();
		});
	}

}
