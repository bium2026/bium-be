package com.bium.domain.ingredient.controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.bium.domain.ingredient.entity.IngredientImage;
import com.bium.domain.ingredient.service.IngredientCatalogService;

import lombok.RequiredArgsConstructor;

/**
 * 재료 사진을 내려준다. 화면의 <img>는 Authorization 헤더를 보낼 수 없어서 로그인 없이 받는다(SecurityConfig).
 * 대신 주소의 키가 무작위라 응답에서 주소를 받은 사람만 볼 수 있다.
 * 사진을 바꾸면 키도 바뀌므로 한 주소의 내용은 바뀌지 않아 오래 캐시해도 된다.
 */
@RestController
@RequiredArgsConstructor
public class IngredientImageController {

	private static final Duration CACHE_DURATION = Duration.ofDays(365);

	private final IngredientCatalogService ingredientCatalogService;

	@GetMapping("/api/ingredient-images/{imageKey}")
	public ResponseEntity<byte[]> getImage(@PathVariable String imageKey) {
		IngredientImage image = ingredientCatalogService.getImage(imageKey);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(image.getContentType()))
			.cacheControl(CacheControl.maxAge(CACHE_DURATION).cachePublic().immutable())
			// 브라우저가 내용을 보고 다른 형식(HTML 등)으로 짐작해 열지 않게 한다
			.header("X-Content-Type-Options", "nosniff")
			.header(HttpHeaders.CONTENT_DISPOSITION, "inline")
			.body(image.getData());
	}

}
