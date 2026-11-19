package com.bium.domain.fridge.dto;

import java.time.DayOfWeek;

// 장보는 요일. MONDAY~SUNDAY 중 하나를 보내고, null을 보내면 요일을 지운다
public record FridgeShoppingDayRequest(DayOfWeek shoppingDay) {
}
