package com.bium.domain.shopping.dto;

import java.time.LocalDate;

import com.bium.domain.shopping.entity.MarketPrice;

// KAMIS 소매가 중 품종·등급을 통틀어 가장 싼 가격. 쇼핑몰 최저가가 아니다
public record MarketPriceResponse(int price, String unit, String kindName, String grade, LocalDate surveyDate,
	Integer weekAgoPrice) {

	public static MarketPriceResponse from(MarketPrice marketPrice) {
		if (marketPrice == null) {
			return null;
		}
		return new MarketPriceResponse(marketPrice.getPrice(), marketPrice.getUnit(), marketPrice.getKindName(),
			marketPrice.getGrade(), marketPrice.getSurveyDate(), marketPrice.getWeekAgoPrice());
	}

}
