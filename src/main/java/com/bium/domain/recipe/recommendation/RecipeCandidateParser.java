package com.bium.domain.recipe.recommendation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.bium.domain.recipe.entity.IngredientSource;
import com.bium.domain.recipe.entity.RecipeDifficulty;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecipeCandidateParser {

	private static final Pattern LEADING_CODE_FENCE = Pattern.compile("^```(?:json)?\\s*", Pattern.CASE_INSENSITIVE);
	private static final Pattern TRAILING_CODE_FENCE = Pattern.compile("```$");
	private static final Pattern DIGITS = Pattern.compile("\\d+");
	private static final int MAX_LOGGED_TEXT_LENGTH = 500;

	private final ObjectMapper objectMapper;

	public List<RecipeCandidate> parse(String text) {
		JsonNode recipes = readJson(text).path("recipes");
		if (!recipes.isArray()) {
			return List.of();
		}

		List<RecipeCandidate> candidates = new ArrayList<>();
		for (JsonNode recipe : recipes) {
			if (recipe.isObject()) {
				candidates.add(toCandidate(recipe));
			}
		}
		return candidates;
	}

	// 음식이 아니라서 recipes를 빈 배열로 준 응답인지 본다. recipes가 없거나 배열이 아닌 잘못된 응답과 구분한다
	public boolean hasNoRecipes(String text) {
		JsonNode recipes = readJson(text).path("recipes");
		return recipes.isArray() && recipes.isEmpty();
	}

	private JsonNode readJson(String text) {
		String cleaned = text == null ? "" : text.strip();
		cleaned = LEADING_CODE_FENCE.matcher(cleaned).replaceFirst("");
		cleaned = TRAILING_CODE_FENCE.matcher(cleaned).replaceFirst("").strip();

		int start = cleaned.indexOf('{');
		int end = cleaned.lastIndexOf('}');
		if (start == -1 || end < start) {
			log.warn("AI 응답에서 JSON을 찾지 못했습니다. text={}", abbreviate(text));
			throw new BusinessException(ErrorCode.AI_REQUEST_FAILED);
		}

		try {
			return objectMapper.readTree(cleaned.substring(start, end + 1));
		} catch (JacksonException exception) {
			log.warn("AI 응답을 JSON으로 해석하지 못했습니다. text={}", abbreviate(text), exception);
			throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, exception);
		}
	}

	private RecipeCandidate toCandidate(JsonNode recipe) {
		JsonNode nutrition = recipe.path("nutrition");
		return new RecipeCandidate(
			toText(recipe.path("name")),
			toText(recipe.path("emoji")),
			toText(recipe.path("summary")),
			toInteger(recipe.path("cookTimeMinutes"), 1, 600),
			toDifficulty(toText(recipe.path("difficulty"))),
			toInteger(recipe.path("servings"), 1, 20),
			toInteger(recipe.path("caloriesKcal"), 0, 5000),
			toIngredients(recipe.path("ingredients")),
			toTexts(recipe.path("steps")),
			toTexts(recipe.path("tips")),
			toInteger(nutrition.path("proteinG"), 0, 1000),
			toInteger(nutrition.path("carbohydrateG"), 0, 1000),
			toInteger(nutrition.path("fatG"), 0, 1000),
			toTexts(recipe.path("fridgeMatches")));
	}

	private List<RecipeCandidate.Ingredient> toIngredients(JsonNode ingredients) {
		List<RecipeCandidate.Ingredient> result = new ArrayList<>();
		for (JsonNode ingredient : ingredients) {
			if (ingredient.isObject()) {
				result.add(new RecipeCandidate.Ingredient(
					toText(ingredient.path("name")),
					toText(ingredient.path("amount")),
					toBoolean(ingredient.path("required"))));
			}
		}
		return result;
	}

	private String toText(JsonNode node) {
		return node.isString() ? node.stringValue().strip() : null;
	}

	private List<String> toTexts(JsonNode node) {
		List<String> result = new ArrayList<>();
		for (JsonNode item : node) {
			String text = toText(item);
			if (text != null && !text.isEmpty()) {
				result.add(text);
			}
		}
		return result;
	}

	private boolean toBoolean(JsonNode node) {
		if (node.isBoolean()) {
			return node.booleanValue();
		}
		return node.isString() && "true".equalsIgnoreCase(node.stringValue().strip());
	}

	// 숫자 대신 "15분", "1,200kcal" 같은 문자열이 와도 숫자만 읽는다. 범위를 벗어나면 null이다
	private Integer toInteger(JsonNode node, int min, int max) {
		Double value = null;
		if (node.isNumber()) {
			value = node.asDouble();
		} else if (node.isString()) {
			Matcher matcher = DIGITS.matcher(node.stringValue().replace(",", ""));
			if (matcher.find()) {
				value = Double.parseDouble(matcher.group());
			}
		}
		if (value == null || value < min || value > max) {
			return null;
		}
		return (int)Math.round(value);
	}

	private String abbreviate(String text) {
		if (text == null || text.length() <= MAX_LOGGED_TEXT_LENGTH) {
			return text;
		}
		return text.substring(0, MAX_LOGGED_TEXT_LENGTH) + "...";
	}

	private RecipeDifficulty toDifficulty(String value) {
		if (value == null) {
			return null;
		}
		return switch (value.toUpperCase(Locale.ROOT)) {
			case "EASY", "쉬움", "하" -> RecipeDifficulty.EASY;
			case "NORMAL", "MEDIUM", "보통", "중" -> RecipeDifficulty.NORMAL;
			case "HARD", "어려움", "상" -> RecipeDifficulty.HARD;
			default -> null;
		};
	}

}
