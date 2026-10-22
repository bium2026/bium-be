package com.bium.domain.recipe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bium.domain.fridge.entity.Fridge;
import com.bium.domain.fridge.entity.FridgeMember;
import com.bium.domain.fridge.entity.MemberRole;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.entity.SectionType;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.user.entity.User;
import com.bium.global.gemini.GeminiClient;

import jakarta.persistence.EntityManager;

/**
 * Gemini 응답을 기다리는 동안에는 트랜잭션이 열려 있지 않아야 한다.
 * 테스트 트랜잭션 안에서는 확인할 수 없어서 데이터를 직접 넣고 지운다.
 */
@SpringBootTest
class RecipeServiceTransactionTest {

	private static final String GEMINI_RESPONSE = """
		{"recipes": [
		  {"name": "두부 조림", "steps": ["두부를 굽는다", "간장에 졸인다"],
		   "ingredients": [{"name": "두부", "amount": "1모", "required": true},
		                   {"name": "간장", "amount": "2큰술", "required": true}],
		   "fridgeMatches": ["두부"]}
		]}
		""";

	@Autowired
	private RecipeService recipeService;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private EntityManager em;

	@Autowired
	private Clock clock;

	@MockitoBean
	private GeminiClient geminiClient;

	private Long userId;
	private Long fridgeId;

	@Test
	void Gemini를_기다리는_동안_트랜잭션을_열어_두지_않는다() {
		transactionTemplate.executeWithoutResult(status -> {
			User user = em.merge(new User("tx-user", "password", "유저"));
			Fridge fridge = em.merge(new Fridge("냉장고"));
			em.persist(new FridgeMember(fridge, user, MemberRole.OWNER));
			em.persist(FridgeItem.builder()
				.fridge(fridge)
				.name("두부")
				.quantity(BigDecimal.ONE)
				.storage(StorageType.FRIDGE)
				.section(SectionType.CENTER)
				.sortOrder(0)
				.expiresAt(LocalDate.now(clock).plusDays(1))
				.build());
			userId = user.getId();
			fridgeId = fridge.getId();
		});
		AtomicBoolean transactionActiveDuringAiCall = new AtomicBoolean(true);
		given(geminiClient.generateJson(anyString())).willAnswer(invocation -> {
			transactionActiveDuringAiCall.set(TransactionSynchronizationManager.isActualTransactionActive());
			return GEMINI_RESPONSE;
		});

		recipeService.recommend(userId, fridgeId, false);

		assertThat(transactionActiveDuringAiCall).isFalse();
	}

	@AfterEach
	void tearDown() {
		transactionTemplate.executeWithoutResult(status -> {
			em.createQuery("delete from RecipeIngredient").executeUpdate();
			em.createQuery("delete from Recipe").executeUpdate();
			em.createQuery("delete from FridgeItem").executeUpdate();
			em.createQuery("delete from FridgeMember").executeUpdate();
			em.createQuery("delete from Fridge").executeUpdate();
			em.createQuery("delete from User").executeUpdate();
		});
	}

}
