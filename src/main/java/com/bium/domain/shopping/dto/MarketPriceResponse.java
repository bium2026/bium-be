package com.bium.domain.shopping.dto;

import java.time.LocalDate;

import com.bium.domain.shopping.entity.MarketPrice;

// KAMIS 평균 소매가. 쇼핑몰 최저가가 아니다
public record MarketPriceResponse(int price, String unit, String kindName, LocalDate surveyDate,
	Integer weekAgoPrice) {

	public static MarketPriceResponse from(MarketPrice marketPrice) {
		if (marketPrice == null) {
			return null;
		}
		return new MarketPriceResponse(marketPrice.getPrice(), marketPrice.getUnit(), marketPrice.getKindName(),
			marketPrice.getSurveyDate(), marketPrice.getWeekAgoPrice());
	}

}
