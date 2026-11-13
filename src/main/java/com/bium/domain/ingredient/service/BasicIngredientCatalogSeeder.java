package com.bium.domain.ingredient.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.bium.domain.ingredient.entity.IngredientCatalog;
import com.bium.domain.ingredient.entity.IngredientCategory;
import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.ingredient.repository.IngredientCatalogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 자주 쓰는 재료(resources/catalog/basic-ingredients.tsv)를 서버를 켤 때 공용 도감(owner_id NULL)에 넣어 둔다.
 * 사용자가 도감에 재료를 하나씩 추가하지 않아도 바로 냉장고에 넣을 수 있게 하려는 것이다.
 * 공용 도감에 없는 이름만 넣고, 이미 있는 기본 재료는 바꾸지 않아서 서버를 다시 켜도 같은 재료가 늘지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ingredient-catalog", name = "seed-basic-ingredients", havingValue = "true",
	matchIfMissing = true)
@RequiredArgsConstructor
public class BasicIngredientCatalogSeeder implements ApplicationRunner {

	static final String RESOURCE = "catalog/basic-ingredients.tsv";
	private static final int COLUMNS = 5;

	private final IngredientCatalogRepository ingredientCatalogRepository;

	@Override
	public void run(ApplicationArguments args) {
		try {
			int saved = seed(readBasicIngredients());
			if (saved > 0) {
				log.info("기본 재료 {}개를 공용 도감에 넣었습니다.", saved);
			}
		} catch (DataAccessException | UncheckedIOException | IllegalArgumentException | IllegalStateException exception) {
			// 기본 재료가 없어도 사용자가 직접 추가한 재료는 쓸 수 있으므로 서버는 켠다
			log.error("기본 재료를 공용 도감에 넣지 못했습니다.", exception);
		}
	}

	// 넣은 재료 수를 돌려준다. saveAll 한 번으로 저장해서, 중간에 실패하면 하나도 넣지 않는다
	int seed(List<BasicIngredient> ingredients) {
		Set<String> existing = ingredientCatalogRepository.findAllByOwnerIsNullOrderByIdAsc().stream()
			.map(IngredientCatalog::getName)
			.collect(Collectors.toSet());
		List<IngredientCatalog> added = ingredients.stream()
			.filter(ingredient -> !existing.contains(ingredient.name()))
			.map(BasicIngredient::toCatalog)
			.toList();
		ingredientCatalogRepository.saveAll(added);
		return added.size();
	}

	List<BasicIngredient> readBasicIngredients() {
		try (BufferedReader reader = new BufferedReader(
			new InputStreamReader(new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
			return reader.lines()
				.filter(line -> !line.isBlank() && !line.startsWith("#") && !line.startsWith("name\t"))
				.map(BasicIngredientCatalogSeeder::parse)
				.toList();
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private static BasicIngredient parse(String line) {
		String[] columns = line.split("\t", -1);
		if (columns.length != COLUMNS) {
			throw new IllegalStateException(RESOURCE + "의 칸 수가 " + COLUMNS + "개가 아닙니다: " + line);
		}
		return new BasicIngredient(columns[0], columns[1], IngredientCategory.valueOf(columns[2]),
			StorageType.valueOf(columns[3]), Integer.valueOf(columns[4]));
	}

	record BasicIngredient(
		String name,
		String emoji,
		IngredientCategory category,
		StorageType defaultStorage,
		Integer shelfLifeDays
	) {

		IngredientCatalog toCatalog() {
			return IngredientCatalog.builder()
				.name(name)
				.emoji(emoji)
				.category(category)
				.defaultStorage(defaultStorage)
				.shelfLifeDays(shelfLifeDays)
				.build();
		}

	}

}
