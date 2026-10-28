package com.bium.domain.ingredient.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
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
import com.bium.domain.ingredient.dto.FridgeItemPositionRequest;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.shopping.dto.PurchasedItemMoveRequest;
import com.bium.domain.shopping.entity.PurchaseSource;
import com.bium.domain.shopping.entity.PurchasedItem;
import com.bium.domain.shopping.service.PurchasedItemService;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

/**
 * 같은 냉장고에서 서로 다른 재료를 동시에 옮겨도 둘 다 성공해야 한다.
 * 각 요청이 자기 재료를 먼저 잠그고 냉장고를 기다리면, 순번을 다시 매기다 서로의 재료를 기다리는 교착 상태가 생긴다.
 * 두 요청이 각자 트랜잭션을 열어야 해서 테스트 트랜잭션 없이 직접 연다.
 */
@SpringBootTest
class FridgeItemPositionConcurrencyTest {

	private static final int ROUNDS = 10;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private EntityManager em;

	@Autowired
	private FridgeItemService fridgeItemService;

	@Autowired
	private PurchasedItemService purchasedItemService;

	private Long userId;
	private Long fridgeId;

	@BeforeEach
	void setUp() {
		transactionTemplate.executeWithoutResult(status -> {
			User user = em.merge(new User("position-member", "password", "멤버"));
			Fridge fridge = em.merge(new Fridge("공유 냉장고"));
			em.persist(new FridgeMember(fridge, user, MemberRole.MEMBER));
			userId = user.getId();
			fridgeId = fridge.getId();
		});
	}

	@Test
	void 같은_칸의_두_재료를_동시에_옮겨도_둘_다_옮겨진다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			List<Long> itemIds = createItems(2);
			// 둘 다 칸이 바뀌지 않는 순서 변경이라 버전은 0 그대로다
			List<Object> results = runTogether(
				() -> fridgeItemService.changePosition(userId, itemIds.get(0), position(1)),
				() -> fridgeItemService.changePosition(userId, itemIds.get(1), position(0)));

			assertThat(results).allMatch(result -> !(result instanceof Exception));
			assertThat(sortOrders()).doesNotHaveDuplicates();
		}
	}

	@Test
	void 장본_음식을_넣는_동안_같은_칸의_재료를_옮겨도_둘_다_성공한다() throws Exception {
		for (int round = 0; round < ROUNDS; round++) {
			List<Long> itemIds = createItems(1);
			Long purchasedItemId = createPurchasedItem();
			List<Object> results = runTogether(
				() -> purchasedItemService.moveToFridge(userId, purchasedItemId,
					new PurchasedItemMoveRequest(StorageType.FRIDGE, SectionType.CENTER, 0)),
				() -> fridgeItemService.changePosition(userId, itemIds.get(0), position(0)));

			assertThat(results).allMatch(result -> !(result instanceof Exception));
			assertThat(sortOrders()).doesNotHaveDuplicates();
		}
	}

	private FridgeItemPositionRequest position(int index) {
		return new FridgeItemPositionRequest(StorageType.FRIDGE, SectionType.CENTER, index, 0);
	}

	private List<Object> runTogether(Callable<?> first, Callable<?> second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Object>> futures = new ArrayList<>();
		for (Callable<?> task : List.of(first, second)) {
			futures.add(executor.submit(() -> {
				start.await();
				try {
					return task.call();
				} catch (Exception e) {
					return e;
				}
			}));
		}
		start.countDown();
		List<Object> results = new ArrayList<>();
		for (Future<Object> future : futures) {
			results.add(future.get(30, TimeUnit.SECONDS));
		}
		executor.shutdown();
		return results;
	}

	// 칸을 비우고 순서대로 재료를 새로 넣는다
	private List<Long> createItems(int count) {
		return transactionTemplate.execute(status -> {
			deleteFridgeContents();
			Fridge fridge = em.find(Fridge.class, fridgeId);
			List<Long> ids = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				FridgeItem item = FridgeItem.builder()
					.fridge(fridge)
					.name("재료" + i)
					.quantity(BigDecimal.ONE)
					.storage(StorageType.FRIDGE)
					.section(SectionType.CENTER)
					.sortOrder(i)
					.build();
				em.persist(item);
				ids.add(item.getId());
			}
			return ids;
		});
	}

	private Long createPurchasedItem() {
		return transactionTemplate.execute(status -> {
			PurchasedItem item = PurchasedItem.builder()
				.fridge(em.find(Fridge.class, fridgeId))
				.user(em.find(User.class, userId))
				.name("두부")
				.purchasedAt(LocalDate.of(2026, 10, 27))
				.source(PurchaseSource.MANUAL)
				.build();
			em.persist(item);
			return item.getId();
		});
	}

	private List<Integer> sortOrders() {
		return transactionTemplate.execute(status -> em.createQuery(
				"select i.sortOrder from FridgeItem i where i.fridge.id = :fridgeId and i.storage = :storage"
					+ " and i.section = :section and i.deletedAt is null", Integer.class)
			.setParameter("fridgeId", fridgeId)
			.setParameter("storage", StorageType.FRIDGE)
			.setParameter("section", SectionType.CENTER)
			.getResultList());
	}

	private void deleteFridgeContents() {
		em.createQuery("delete from ActivityLog where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
		em.createQuery("delete from PurchasedItem where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
		em.createQuery("delete from FridgeItem where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
	}

	@AfterEach
	void tearDown() {
		transactionTemplate.executeWithoutResult(status -> {
			deleteFridgeContents();
			em.createQuery("delete from FridgeMember where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from Fridge where id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from User where id = :id").setParameter("id", userId).executeUpdate();
		});
	}

}
