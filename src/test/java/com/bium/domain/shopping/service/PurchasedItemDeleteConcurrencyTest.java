package com.bium.domain.shopping.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.shopping.dto.PurchasedItemMoveRequest;
import com.bium.domain.shopping.entity.PurchaseSource;
import com.bium.domain.shopping.entity.PurchasedItem;
import com.bium.domain.user.entity.User;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

/**
 * 한 멤버가 장본 음식을 냉장고로 옮기는 사이 다른 멤버가 같은 장본 음식을 빼도, 옮긴 기록은 지워지면 안 된다.
 * 두 요청이 각자 트랜잭션을 열어야 해서 테스트 트랜잭션 없이 직접 연다.
 */
@SpringBootTest
class PurchasedItemDeleteConcurrencyTest {

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private EntityManager em;

	@Autowired
	private PurchasedItemService purchasedItemService;

	private Long userId;
	private Long fridgeId;
	private Long purchasedItemId;

	@BeforeEach
	void setUp() {
		transactionTemplate.executeWithoutResult(status -> {
			User user = em.merge(new User("delete-member", "password", "멤버"));
			Fridge fridge = em.merge(new Fridge("공유 냉장고"));
			em.persist(new FridgeMember(fridge, user, MemberRole.MEMBER));
			PurchasedItem item = PurchasedItem.builder()
				.fridge(fridge)
				.user(user)
				.name("두부")
				.emoji("🧊")
				.purchasedAt(LocalDate.of(2026, 11, 12))
				.expiresAt(LocalDate.of(2026, 11, 19))
				.source(PurchaseSource.MANUAL)
				.build();
			em.persist(item);
			userId = user.getId();
			fridgeId = fridge.getId();
			purchasedItemId = item.getId();
		});
	}

	@Test
	void 옮기는_사이에_빼면_옮긴_기록을_지우지_않고_장본_음식이_없다고_알린다() throws Exception {
		PurchasedItemMoveRequest request = new PurchasedItemMoveRequest(StorageType.FRIDGE, SectionType.CENTER, 0);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch moved = new CountDownLatch(1);
		CountDownLatch commit = new CountDownLatch(1);

		// 옮기기를 마치고 커밋하기 직전에 멈춰, 빼기 요청이 그 사이에 들어오게 한다
		Future<?> move = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
			purchasedItemService.moveToFridge(userId, purchasedItemId, request);
			moved.countDown();
			try {
				commit.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}));
		assertThat(moved.await(10, TimeUnit.SECONDS)).isTrue();

		Future<ErrorCode> delete = executor.submit(() -> {
			try {
				purchasedItemService.delete(userId, purchasedItemId);
				return null;
			} catch (BusinessException e) {
				return e.getErrorCode();
			}
		});
		// 빼기 요청이 옮기기의 잠금을 기다리기 시작할 때까지 잠깐 둔 뒤 옮기기를 커밋한다
		Thread.sleep(300);
		commit.countDown();
		move.get(10, TimeUnit.SECONDS);
		ErrorCode deleteError = delete.get(10, TimeUnit.SECONDS);
		executor.shutdown();

		assertThat(deleteError).isEqualTo(ErrorCode.PURCHASED_ITEM_NOT_FOUND);
		PurchasedItem record = transactionTemplate.execute(status -> em.find(PurchasedItem.class, purchasedItemId));
		assertThat(record).isNotNull();
		assertThat(record.getMovedAt()).isNotNull();
	}

	@Test
	void 옮기지_않은_장본_음식은_뺄_수_있다() {
		purchasedItemService.delete(userId, purchasedItemId);

		PurchasedItem deleted = transactionTemplate.execute(status -> em.find(PurchasedItem.class, purchasedItemId));
		assertThat(deleted).isNull();
	}

	@AfterEach
	void tearDown() {
		transactionTemplate.executeWithoutResult(status -> {
			em.createQuery("delete from ActivityLog where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from PurchasedItem where id = :id").setParameter("id", purchasedItemId)
				.executeUpdate();
			em.createQuery("delete from FridgeItem where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from FridgeMember where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from Fridge where id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from User where id = :id").setParameter("id", userId).executeUpdate();
		});
	}

}
