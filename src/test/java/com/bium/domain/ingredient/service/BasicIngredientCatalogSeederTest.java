package com.bium.domain.ingredient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataIntegrityViolationException;

import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.IngredientCategory;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.ingredient.repository.IngredientCatalogRepository;
import com.bium.domain.ingredient.service.BasicIngredientCatalogSeeder.BasicIngredient;

class BasicIngredientCatalogSeederTest {

	private final IngredientCatalogRepository ingredientCatalogRepository = mock(IngredientCatalogRepository.class);
	private final BasicIngredientCatalogSeeder seeder = new BasicIngredientCatalogSeeder(ingredientCatalogRepository);

	@Test
	void 파일의_기본_재료는_이름이_겹치지_않고_도감_추가_규칙을_지킨다() {
		List<BasicIngredient> ingredients = seeder.readBasicIngredients();

		assertThat(ingredients).hasSize(76);
		assertThat(ingredients).extracting(BasicIngredient::name).doesNotHaveDuplicates();
		// 사용자가 도감에 추가할 때의 검증(IngredientCatalogCreateRequest)과 같은 범위를 지킨다
		assertThat(ingredients).allSatisfy(ingredient -> {
			assertThat(ingredient.name()).isNotBlank().hasSizeLessThanOrEqualTo(30).isEqualTo(ingredient.name().strip());
			assertThat(ingredient.emoji()).isNotBlank().hasSizeLessThanOrEqualTo(16);
			assertThat(ingredient.shelfLifeDays()).isBetween(1, 365);
		});
		assertThat(ingredients).extracting(BasicIngredient::category)
			.contains(IngredientCategory.VEGETABLE, IngredientCategory.MEAT, IngredientCategory.DAIRY,
				IngredientCategory.FRUIT, IngredientCategory.ETC);
	}

	@Test
	void 공용_도감에_없는_이름만_넣는다() {
		IngredientCatalog existingOnion = IngredientCatalog.builder().name("양파").emoji("🧅").build();
		given(ingredientCatalogRepository.findAllByOwnerIsNullOrderByIdAsc()).willReturn(List.of(existingOnion));

		int saved = seeder.seed(List.of(
			new BasicIngredient("양파", "🧅", IngredientCategory.VEGETABLE, StorageType.ROOM, 30),
			new BasicIngredient("두부", "🧈", IngredientCategory.ETC, StorageType.FRIDGE, 5)));

		assertThat(saved).isEqualTo(1);
		IngredientCatalog tofu = savedCatalogs().get(0);
		assertThat(tofu.getName()).isEqualTo("두부");
		assertThat(tofu.isShared()).isTrue();
		assertThat(tofu.getCategory()).isEqualTo(IngredientCategory.ETC);
		assertThat(tofu.getDefaultStorage()).isEqualTo(StorageType.FRIDGE);
		assertThat(tofu.getShelfLifeDays()).isEqualTo(5);
	}

	@Test
	void 저장하다_실패해도_서버는_켜진다() {
		given(ingredientCatalogRepository.findAllByOwnerIsNullOrderByIdAsc()).willReturn(List.of());
		willThrow(new DataIntegrityViolationException("duplicate")).given(ingredientCatalogRepository).saveAll(any());

		seeder.run(new DefaultApplicationArguments());

		verify(ingredientCatalogRepository).saveAll(any());
	}

	@SuppressWarnings("unchecked")
	private List<IngredientCatalog> savedCatalogs() {
		ArgumentCaptor<List<IngredientCatalog>> captor = ArgumentCaptor.forClass(List.class);
		verify(ingredientCatalogRepository).saveAll(captor.capture());
		return captor.getValue();
	}

}
