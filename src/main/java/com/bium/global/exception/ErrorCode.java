package com.bium.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),
	DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
	FRIDGE_NOT_FOUND(HttpStatus.NOT_FOUND, "냉장고를 찾을 수 없습니다."),
	FRIDGE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "재료를 찾을 수 없습니다."),
	CATALOG_NOT_FOUND(HttpStatus.NOT_FOUND, "도감에서 재료를 찾을 수 없습니다."),
	DUPLICATE_CATALOG_NAME(HttpStatus.CONFLICT, "이미 도감에 있는 재료입니다."),
	PURCHASED_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "장본 음식을 찾을 수 없습니다."),
	CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "다른 멤버가 먼저 수정했습니다. 새로고침한 뒤 다시 시도해 주세요."),
	RECIPE_NOT_FOUND(HttpStatus.NOT_FOUND, "레시피를 찾을 수 없습니다."),
	NO_USABLE_INGREDIENT(HttpStatus.UNPROCESSABLE_CONTENT, "유통기한이 남아 있는 재료가 없어 추천할 수 없습니다."),
	TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
	AI_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "레시피 추천에 실패했습니다. 잠시 후 다시 시도해 주세요."),
	RECIPE_NO_RESULT(HttpStatus.BAD_GATEWAY, "추천할 수 있는 레시피를 받지 못했습니다. 다시 시도해 주세요."),
	AI_QUOTA_EXCEEDED(HttpStatus.SERVICE_UNAVAILABLE, "AI 추천 사용 한도를 넘었습니다. 잠시 후 다시 시도해 주세요."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String message;

}
