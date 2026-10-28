package com.bium.domain.shopping.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
 * 같은 장본 음식을 두 멤버가 동시에 냉장고로 끌어다 놓아도 냉장고 재료는 하나만 생겨야 한다.
 * 두 요청이 각자 트랜잭션을 열어야 해서 테스트 트랜잭션 없이 직접 연다.
 */
@SpringBootTest
class PurchasedItemMoveConcurrencyTest {

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
			User user = em.merge(new User("move-member", "password", "멤버"));
			Fridge fridge = em.merge(new Fridge("공유 냉장고"));
			em.persist(new FridgeMember(fridge, user, MemberRole.MEMBER));
			PurchasedItem item = PurchasedItem.builder()
				.fridge(fridge)
				.user(user)
				.name("두부")
				.emoji("🧊")
				.purchasedAt(LocalDate.of(2026, 10, 27))
				.expiresAt(LocalDate.of(2026, 11, 3))
				.source(PurchaseSource.MANUAL)
				.build();
			em.persist(item);
			userId = user.getId();
			fridgeId = fridge.getId();
			purchasedItemId = item.getId();
		});
	}

	@Test
	void 동시에_두_번_옮겨도_냉장고_재료는_하나만_생긴다() throws Exception {
		PurchasedItemMoveRequest request = new PurchasedItemMoveRequest(StorageType.FRIDGE, SectionType.CENTER, 0);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<ErrorCode>> results = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			results.add(executor.submit(() -> {
				start.await();
				try {
					purchasedItemService.moveToFridge(userId, purchasedItemId, request);
					return null;
				} catch (BusinessException e) {
					return e.getErrorCode();
				}
			}));
		}
		start.countDown();
		List<ErrorCode> errors = new ArrayList<>();
		for (Future<ErrorCode> result : results) {
			errors.add(result.get(10, TimeUnit.SECONDS));
		}
		executor.shutdown();

		assertThat(errors).containsExactlyInAnyOrder(null, ErrorCode.PURCHASED_ITEM_NOT_FOUND);
		Long itemCount = transactionTemplate.execute(status -> em.createQuery(
				"select count(i) from FridgeItem i where i.fridge.id = :fridgeId", Long.class)
			.setParameter("fridgeId", fridgeId)
			.getSingleResult());
		assertThat(itemCount).isEqualTo(1);
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
