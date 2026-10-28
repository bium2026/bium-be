package com.bium.domain.shopping.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.bium.domain.recipe.entity.IngredientSource;
import com.bium.domain.recipe.entity.Recipe;
import com.bium.domain.recipe.entity.RecipeIngredient;
import com.bium.domain.recipe.entity.RecipeType;
import com.bium.domain.shopping.dto.MissingIngredientAddRequest;
import com.bium.domain.user.entity.User;

import jakarta.persistence.EntityManager;

/**
 * 같은 부족 재료를 동시에 담아도 장보기 목록에는 한 번만 들어가야 한다.
 * 두 요청이 각자 트랜잭션을 열어야 해서 테스트 트랜잭션 없이 직접 연다.
 */
@SpringBootTest
class MissingIngredientConcurrencyTest {

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private EntityManager em;

	@Autowired
	private MissingIngredientService missingIngredientService;

	private Long userId;
	private Long fridgeId;
	private Long recipeId;

	@BeforeEach
	void setUp() {
		transactionTemplate.executeWithoutResult(status -> {
			User user = em.merge(new User("missing-member", "password", "멤버"));
			Fridge fridge = em.merge(new Fridge("공유 냉장고"));
			em.persist(new FridgeMember(fridge, user, MemberRole.MEMBER));
			Recipe recipe = Recipe.builder().fridge(fridge).type(RecipeType.TARGET).title("두부조림").build();
			em.persist(recipe);
			em.persist(RecipeIngredient.builder().recipe(recipe).name("두부").source(IngredientSource.MISSING).build());
			userId = user.getId();
			fridgeId = fridge.getId();
			recipeId = recipe.getId();
		});
	}

	@Test
	void 같은_부족_재료를_동시에_담아도_한_번만_담는다() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			results.add(executor.submit(() -> {
				start.await();
				return missingIngredientService.add(userId, recipeId, new MissingIngredientAddRequest(List.of("두부")))
					.items().size();
			}));
		}
		start.countDown();
		List<Integer> addedCounts = new ArrayList<>();
		for (Future<Integer> result : results) {
			addedCounts.add(result.get(10, TimeUnit.SECONDS));
		}
		executor.shutdown();

		assertThat(addedCounts).containsExactlyInAnyOrder(1, 0);
		Long count = transactionTemplate.execute(status -> em.createQuery(
				"select count(s) from ShoppingListItem s where s.fridge.id = :id", Long.class)
			.setParameter("id", fridgeId)
			.getSingleResult());
		assertThat(count).isEqualTo(1);
	}

	@AfterEach
	void tearDown() {
		transactionTemplate.executeWithoutResult(status -> {
			em.createQuery("delete from ShoppingListItem where fridge.id = :id").setParameter("id", fridgeId)
				.executeUpdate();
			em.createQuery("delete from RecipeIngredient where recipe.id = :id").setParameter("id", recipeId)
				.executeUpdate();
			em.createQuery("delete from Recipe where id = :id").setParameter("id", recipeId).executeUpdate();
			em.createQuery("delete from FridgeMember where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from Fridge where id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from User where id = :id").setParameter("id", userId).executeUpdate();
		});
	}

}
