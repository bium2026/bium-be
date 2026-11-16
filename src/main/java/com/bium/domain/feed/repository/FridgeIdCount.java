package com.bium.domain.feed.repository;

// 냉장고별로 센 수 (좋아요 수처럼 여러 냉장고를 한 번에 셀 때 쓴다)
public interface FridgeIdCount {

	Long getFridgeId();

	long getTotal();

}
