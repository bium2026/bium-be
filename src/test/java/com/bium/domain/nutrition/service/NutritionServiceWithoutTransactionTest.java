package com.bium.domain.nutrition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.nutrition.dto.NutritionResponse;
import com.bium.domain.nutrition.entity.FoodNutrition;
import com.bium.domain.nutrition.repository.FoodNutritionRepository;
import com.bium.domain.user.entity.User;
import com.bium.global.nutrition.FoodNutritionClient;
import com.bium.global.nutrition.FoodNutritionClient.Food;
import com.bium.global.nutrition.FoodNutritionClient.SearchPage;

import jakarta.persistence.EntityManager;

/**
 * 영양 정보 조회는 식약처를 기다리는 동안 DB 연결을 잡지 않으려고 트랜잭션 없이 돈다.
 * 테스트 트랜잭션이 있으면 지연 로딩과 저장 충돌이 실제와 다르게 동작해서 테스트 트랜잭션 없이 확인한다.
 */
@SpringBootTest
class NutritionServiceWithoutTransactionTest {

	private static final Food TOFU = new Food("R1", "두부", "원재료성", new BigDecimal("100"), new BigDecimal("84"),
		new BigDecimal("1.8"), new BigDecimal("9.3"), new BigDecimal("4.6"));

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private EntityManager em;

	@Autowired
	private NutritionService nutritionService;

	@Autowired
	private FoodNutritionRepository foodNutritionRepository;

	@Autowired
	private Clock clock;

	@MockitoBean
	private FoodNutritionClient foodNutritionClient;

	private Long userId;
	private Long fridgeId;
	private Long catalogId;
	private Long itemId;

	@BeforeEach
	void setUp() {
		transactionTemplate.executeWithoutResult(status -> {
			User user = em.merge(new User("nutrition-member", "password", "멤버"));
			Fridge fridge = em.merge(new Fridge("영양 냉장고"));
			em.persist(new FridgeMember(fridge, user, MemberRole.MEMBER));
			IngredientCatalog catalog = em.merge(IngredientCatalog.builder()
				.owner(user)
				.name("두부")
				.defaultStorage(StorageType.FRIDGE)
				.build());
			FridgeItem item = FridgeItem.builder()
				.fridge(fridge)
				.catalog(catalog)
				.name("엄마가 준 두부")
				.quantity(BigDecimal.ONE)
				.storage(StorageType.FRIDGE)
				.section(SectionType.CENTER)
				.sortOrder(0)
				.build();
			em.persist(item);
			userId = user.getId();
			fridgeId = fridge.getId();
			catalogId = catalog.getId();
			itemId = item.getId();
		});
	}

	@AfterEach
	void tearDown() {
		transactionTemplate.executeWithoutResult(status -> {
			em.createQuery("delete from FoodNutrition").executeUpdate();
			em.createQuery("delete from FridgeItem where id = :id").setParameter("id", itemId).executeUpdate();
			em.createQuery("delete from IngredientCatalog where id = :id").setParameter("id", catalogId).executeUpdate();
			em.createQuery("delete from FridgeMember where fridge.id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from Fridge where id = :id").setParameter("id", fridgeId).executeUpdate();
			em.createQuery("delete from User where id = :id").setParameter("id", userId).executeUpdate();
		});
	}

	@Test
	void 트랜잭션_밖에서도_도감_이름으로_찾아_저장한다() {
		given(foodNutritionClient.searchByName("두부", 1)).willReturn(new SearchPage(List.of(TOFU), false));

		NutritionResponse response = nutritionService.getItemNutrition(userId, itemId);

		assertThat(response.foodName()).isEqualTo("두부");
		assertThat(foodNutritionRepository.findBySearchName("두부")).isPresent();
	}

	@Test
	void 식약처를_기다리는_사이_다른_요청이_같은_이름을_먼저_저장하면_그_결과를_쓴다() {
		TransactionTemplate otherRequest = new TransactionTemplate(transactionTemplate.getTransactionManager());
		otherRequest.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		given(foodNutritionClient.searchByName("두부", 1)).willAnswer(invocation -> {
			otherRequest.executeWithoutResult(status -> {
				FoodNutrition nutrition = new FoodNutrition("두부");
				nutrition.update(TOFU, LocalDateTime.now(clock));
				em.persist(nutrition);
			});
			return new SearchPage(List.of(TOFU), false);
		});

		NutritionResponse response = nutritionService.getItemNutrition(userId, itemId);

		assertThat(response.foodName()).isEqualTo("두부");
		assertThat(foodNutritionRepository.count()).isEqualTo(1);
	}

}
