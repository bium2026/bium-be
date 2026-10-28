package com.bium.domain.recipe.entity;

public enum IngredientSource {

	// 냉장고에 있는 재료
	FRIDGE,
	// 소금, 간장 같은 기본 양념
	BASIC,
	// 만들고 싶은 음식에 필요한데 냉장고에 없어 사야 하는 재료
	MISSING

}
