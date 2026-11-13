package com.bium.domain.ingredient.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import com.bium.domain.fridge.repository.FridgeRepository;
import com.bium.domain.ingredient.dto.FridgeItemBulkDeleteRequest;
import com.bium.domain.ingredient.dto.FridgeItemUpdateRequest;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.recipe.dto.CookingRequest;
import com.bium.domain.recipe.entity.Recipe;
import com.bium.domain.recipe.service.CookingService;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

/**
 * 재료를 고치거나 지우거나 요리를 되돌리는 요청은 재료 행보다 냉장고 행을 먼저 잠가야 한다.
 * 활동 기록을 넣을 때 MySQL이 외래 키 확인으로 냉장고 행의 잠금을 기다리므로, 재료를 먼저 잡으면
 * 냉장고 → 칸의 재료 순서로 잠그는 옮기기 요청과 교착 상태가 생긴다.
 * H2는 외래 키 확인에서 잠그지 않아 교착 상태를 그대로 재현할 수 없어서, 냉장고를 잡아 둔 동안 기다리는지로 순서를 확인한다.
 */
@SpringBootTest
class FridgeItemWriteLockOrderTest {

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private EntityManager em;

	@Autowired
	private FridgeRepository fridgeRepository;

	@Autowired
	private FridgeItemService fridgeItemService;

	@Autowired
	private CookingService cookingService;

	private Long userId;
	private Long fridgeId;
	private Long recipeId;
	private Long tofuId;
	private Long milkId;
	private Long cookingLogId;

	@BeforeEach
	void setUp() {
		transactionTemplate.executeWithoutResult(status -> {
			User user = em.merge(new User("lock-order-member", "password", "멤버"));
			Fridge fridge = em.merge(new Fridge("공유 냉장고"));
			em.persist(new FridgeMember(fridge, user, MemberRole.MEMBER));
			Recipe recipe = Recipe.builder().fridge(fridge).title("두부조림").build();
			em.persist(recipe);
			FridgeItem tofu = item(fridge, "두부", 0);
			FridgeItem milk = item(fridge, "우유", 1);
			em.persist(tofu);
			em.persist(milk);
			userId = user.getId();
			fridgeId = fridge.getId();
			recipeId = recipe.getId();
			tofuId = tofu.getId();
			milkId = milk.getId();
		});
		cookingLogId = cookingService.cook(userId, recipeId,
			new CookingRequest(List.of(new CookingRequest.UsedItem(tofuId, 0)))).id();
	}

	@Test
	void 재료를_고칠_때_냉장고부터_잠근다() throws Exception {
		assertWaitsForFridgeLock(() ->
			fridgeItemService.update(userId, milkId, new FridgeItemUpdateRequest("저지방 우유", "🥛", null, 0)));
	}

	@Test
	void 재료를_지울_때_냉장고부터_잠근다() throws Exception {
		assertWaitsForFridgeLock(() -> fridgeItemService.delete(userId, milkId, 0));
	}

	@Test
	void 여러_재료를_지울_때_냉장고부터_잠근다() throws Exception {
		assertWaitsForFridgeLock(() -> fridgeItemService.deleteAll(userId, fridgeId,
			new FridgeItemBulkDeleteRequest(List.of(new FridgeItemBulkDeleteRequest.Item(milkId, 0)))));
	}

	@Test
	void 요리를_되돌릴_때_냉장고부터_잠근다() throws Exception {
		assertWaitsForFridgeLock(() -> cookingService.undo(userId, cookingLogId));
	}

	// 다른 트랜잭션이 냉장고 행을 잡고 있는 동안 요청이 끝나지 않고 기다리다, 놓아주면 성공하는지 본다
	private void assertWaitsForFridgeLock(Runnable request) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
			fridgeRepository.findByIdForUpdate(fridgeId).orElseThrow();
			locked.countDown();
			try {
				release.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}));
		assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

		Future<?> waiting = executor.submit(request);
		Thread.sleep(300);
		boolean finishedWhileLocked = waiting.isDone();
		release.countDown();
		holder.get(10, TimeUnit.SECONDS);
		waiting.get(10, TimeUnit.SECONDS);
		executor.shutdown();

		assertThat(finishedWhileLocked).as("냉장고 잠금을 기다리지 않고 끝났다").isFalse();
	}

	private FridgeItem item(Fridge fridge, String name, int sortOrder) {
		return FridgeItem.builder()
			.fridge(fridge)
			.name(name)
			.quantity(BigDecimal.ONE)
			.storage(StorageType.FRIDGE)
			.section(SectionType.CENTER)
			.sortOrder(sortOrder)
			.build();
	}

	@AfterEach
	void tearDown() {
		transactionTemplate.executeWithoutResult(status -> {
			em.createQuery("delete from ActivityLog where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from CookingLogItem").executeUpdate();
			em.createQuery("delete from CookingLog where id = :id").setParameter("id", cookingLogId).executeUpdate();
			em.createQuery("delete from FridgeItem where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from Recipe where id = :id").setParameter("id", recipeId).executeUpdate();
			em.createQuery("delete from FridgeMember where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from Fridge where id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from User where id = :id").setParameter("id", userId).executeUpdate();
		});
	}

}
