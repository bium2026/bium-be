package com.bium.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.recipe.dto.CookingRequest;
import com.bium.domain.recipe.entity.Recipe;
import com.bium.domain.user.entity.User;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

/**
 * 되돌리기를 동시에 두 번 보내도 재료는 한 번만 복원돼야 한다.
 * 두 요청이 각자 트랜잭션을 열어야 해서 테스트 트랜잭션 없이 직접 연다.
 */
@SpringBootTest
class CookingUndoConcurrencyTest {

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private EntityManager em;

	@Autowired
	private CookingService cookingService;

	private Long userId;
	private Long fridgeId;
	private Long recipeId;
	private Long itemId;
	private Long cookingLogId;

	@BeforeEach
	void setUp() {
		transactionTemplate.executeWithoutResult(status -> {
			User user = em.merge(new User("undo-member", "password", "멤버"));
			Fridge fridge = em.merge(new Fridge("공유 냉장고"));
			em.persist(new FridgeMember(fridge, user, MemberRole.MEMBER));
			Recipe recipe = Recipe.builder().fridge(fridge).title("두부조림").build();
			em.persist(recipe);
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
			recipeId = recipe.getId();
			itemId = item.getId();
		});
		cookingLogId = cookingService.cook(userId, recipeId,
			new CookingRequest(List.of(new CookingRequest.UsedItem(itemId, 0)))).id();
	}

	@Test
	void 동시에_두_번_되돌려도_재료는_한_번만_복원한다() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<ErrorCode>> results = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			results.add(executor.submit(() -> {
				start.await();
				try {
					cookingService.undo(userId, cookingLogId);
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

		assertThat(errors).containsExactlyInAnyOrder(null, ErrorCode.ALREADY_UNDONE);
		FridgeItem restored = transactionTemplate.execute(status -> em.find(FridgeItem.class, itemId));
		assertThat(restored.isDeleted()).isFalse();
		assertThat(restored.getQuantity()).isEqualByComparingTo(BigDecimal.ONE);
	}

	@AfterEach
	void tearDown() {
		transactionTemplate.executeWithoutResult(status -> {
			em.createQuery("delete from CookingLogItem").executeUpdate();
			em.createQuery("delete from CookingLog where id = :id").setParameter("id", cookingLogId).executeUpdate();
			em.createQuery("delete from FridgeItem where id = :id").setParameter("id", itemId).executeUpdate();
			em.createQuery("delete from Recipe where id = :id").setParameter("id", recipeId).executeUpdate();
			em.createQuery("delete from FridgeMember where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from Fridge where id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from User where id = :id").setParameter("id", userId).executeUpdate();
		});
	}

}
