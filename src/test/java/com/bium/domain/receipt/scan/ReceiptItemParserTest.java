package com.bium.domain.receipt.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.bium.domain.ingredient.entity.StorageType;
import com.bium.domain.receipt.dto.ReceiptScanResponse;
import com.bium.global.exception.BusinessException;
import com.bium.global.exception.ErrorCode;

import tools.jackson.databind.json.JsonMapper;

class ReceiptItemParserTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

	private final ReceiptItemParser parser = new ReceiptItemParser(JsonMapper.builder().build());

	@Test
	void 영수증이면_적힌_품목을_후보로_바꾼다() {
		List<ReceiptScanResponse.Item> items = parser.parse("""
			{"isReceipt": true, "items": [
			  {"receiptText": "서울우유 1L", "name": "우유", "emoji": "🥛", "storage": "FRIDGE", "shelfLifeDays": 10},
			  {"receiptText": "비비고 왕교자 1.05kg", "name": "왕교자 만두", "emoji": "🥟", "storage": "FREEZER", "shelfLifeDays": null}
			]}
			""", TODAY);

		assertThat(items).containsExactly(
			new ReceiptScanResponse.Item("우유", "🥛", StorageType.FRIDGE, TODAY.plusDays(10)),
			new ReceiptScanResponse.Item("왕교자 만두", "🥟", StorageType.FREEZER, null));
	}

	@Test
	void 영수증이_아니라고_답하면_품목이_있어도_인식_실패로_알린다() {
		assertNotRecognized("""
			{"isReceipt": false, "items": [{"receiptText": "사과", "name": "사과", "emoji": "🍎", "storage": "FRIDGE", "shelfLifeDays": 14}]}
			""");
	}

	@Test
	void 영수증인지_답하지_않았거나_참거짓이_아니면_인식_실패로_알린다() {
		assertNotRecognized("""
			{"items": [{"receiptText": "사과", "name": "사과"}]}
			""");
		assertNotRecognized("""
			{"isReceipt": "true", "items": [{"receiptText": "사과", "name": "사과"}]}
			""");
	}

	@Test
	void 영수증에_적힌_상품명을_옮기지_않은_품목은_뺀다() {
		List<ReceiptScanResponse.Item> items = parser.parse("""
			{"isReceipt": true, "items": [
			  {"name": "사과", "emoji": "🍎", "storage": "FRIDGE", "shelfLifeDays": 14},
			  {"receiptText": "  ", "name": "바나나", "emoji": "🍌", "storage": "ROOM", "shelfLifeDays": 5},
			  {"receiptText": "국산 대파 1단", "name": "대파", "emoji": "🌿", "storage": "FRIDGE", "shelfLifeDays": 7}
			]}
			""", TODAY);

		assertThat(items).extracting(ReceiptScanResponse.Item::name).containsExactly("대파");
	}

	@Test
	void 영수증이지만_식재료가_없으면_빈_목록을_돌려준다() {
		assertThat(parser.parse("{\"isReceipt\": true, \"items\": []}", TODAY)).isEmpty();
	}

	@Test
	void JSON이_아니면_읽기_실패로_알린다() {
		assertThatThrownBy(() -> parser.parse("영수증이 아닙니다", TODAY))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException)exception).getErrorCode())
			.isEqualTo(ErrorCode.RECEIPT_SCAN_FAILED);
	}

	private void assertNotRecognized(String text) {
		assertThatThrownBy(() -> parser.parse(text, TODAY))
			.isInstanceOf(BusinessException.class)
			.extracting(exception -> ((BusinessException)exception).getErrorCode())
			.isEqualTo(ErrorCode.RECEIPT_NOT_RECOGNIZED);
	}

}
