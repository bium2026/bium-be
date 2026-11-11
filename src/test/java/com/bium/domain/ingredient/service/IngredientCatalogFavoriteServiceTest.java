package com.bium.domain.ingredient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.bium.domain.ingredient.dto.IngredientCatalogResponse;
import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.user.entity.User;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class IngredientCatalogFavoriteServiceTest {

	@Autowired
	private IngredientCatalogService ingredientCatalogService;

	@Autowired
	private EntityManager em;

	private User user;
	private IngredientCatalog tofu;
	private IngredientCatalog milk;
	private IngredientCatalog egg;

	@BeforeEach
	void setUp() {
		user = em.merge(new User("user", "password", "사용자"));
		tofu = catalog(user, "두부");
		milk = catalog(user, "우유");
		egg = catalog(user, "달걀");
	}

	@Test
	void 즐겨찾기한_재료가_목록_위에_오고_각_그룹은_최근_추가_순서를_지킨다() {
		ingredientCatalogService.addFavorite(user.getId(), tofu.getId());
		ingredientCatalogService.addFavorite(user.getId(), milk.getId());
		em.flush();
		em.clear();

		assertThat(ingredientCatalogService.getMyCatalog(user.getId()).ingredients())
			.extracting(IngredientCatalogResponse::name, IngredientCatalogResponse::favorite)
			.containsExactly(
				tuple("우유", true),
				tuple("두부", true),
				tuple("달걀", false));
	}

	@Test
	void 즐겨찾기가_없으면_최근_추가_순서_그대로_준다() {
		assertThat(ingredientCatalogService.getMyCatalog(user.getId()).ingredients())
			.extracting(IngredientCatalogResponse::name)
			.containsExactly("달걀", "우유", "두부");
	}

	@Test
	void 같은_재료를_두_번_즐겨찾기해도_한_번만_저장한다() {
		ingredientCatalogService.addFavorite(user.getId(), tofu.getId());
		IngredientCatalogResponse response = ingredientCatalogService.addFavorite(user.getId(), tofu.getId());
		em.flush();

		assertThat(response.favorite()).isTrue();
		assertThat(countFavorites()).isEqualTo(1);
	}

	@Test
	void 즐겨찾기를_해제하면_원래_순서로_돌아간다() {
		ingredientCatalogService.addFavorite(user.getId(), tofu.getId());
		em.flush();

		IngredientCatalogResponse response = ingredientCatalogService.removeFavorite(user.getId(), tofu.getId());
		em.flush();
		em.clear();

		assertThat(response.favorite()).isFalse();
		assertThat(countFavorites()).isZero();
		assertThat(ingredientCatalogService.getMyCatalog(user.getId()).ingredients())
			.extracting(IngredientCatalogResponse::name)
			.containsExactly("달걀", "우유", "두부");
	}

	@Test
	void 즐겨찾기하지_않은_재료를_해제해도_오류가_나지_않는다() {
		assertThat(ingredientCatalogService.removeFavorite(user.getId(), tofu.getId()).favorite()).isFalse();
	}

	@Test
	void 다른_사용자의_재료는_즐겨찾기할_수_없고_없는_재료와_같은_404를_준다() {
		User other = em.merge(new User("other", "password", "남"));
		IngredientCatalog othersCatalog = catalog(other, "남의 두부");

		assertThatThrownBy(() -> ingredientCatalogService.addFavorite(user.getId(), othersCatalog.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.CATALOG_NOT_FOUND);
		assertThatThrownBy(() -> ingredientCatalogService.removeFavorite(user.getId(), othersCatalog.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.CATALOG_NOT_FOUND);
		assertThat(countFavorites()).isZero();
	}

	@Test
	void 없는_재료를_즐겨찾기하면_404를_준다() {
		assertThatThrownBy(() -> ingredientCatalogService.addFavorite(user.getId(), 999999L))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.CATALOG_NOT_FOUND);
	}

	@Test
	void 즐겨찾기한_재료를_도감에서_지우면_즐겨찾기도_함께_지운다() {
		ingredientCatalogService.addFavorite(user.getId(), tofu.getId());
		// 즐겨찾기와 도감 삭제는 서로 다른 요청이라 영속성 컨텍스트를 비운다
		em.flush();
		em.clear();

		ingredientCatalogService.delete(user.getId(), tofu.getId());
		em.flush();
		em.clear();

		assertThat(countFavorites()).isZero();
		assertThat(em.find(IngredientCatalog.class, tofu.getId())).isNull();
	}

	private IngredientCatalog catalog(User owner, String name) {
		IngredientCatalog catalog = IngredientCatalog.builder().owner(owner).name(name).emoji("🥬").build();
		em.persist(catalog);
		return catalog;
	}

	private long countFavorites() {
		return em.createQuery("select count(f) from IngredientFavorite f", Long.class)
			.getSingleResult();
	}

}
