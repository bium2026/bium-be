package com.bium.global.kamis;

/**
 * KAMIS 응답의 품목 한 줄. 가격은 쉼표를 뺀 숫자이고, 값이 없으면 null이다.
 */
public record KamisPrice(String itemName, String kindName, String rank, String unit, Integer price,
	Integer weekAgoPrice) {
}
