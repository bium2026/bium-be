package com.bium.domain.ingredient.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bium.domain.auth.service.JwtProvider;
import com.bium.domain.ingredient.entity.FridgeItem;
import com.bium.domain.ingredient.service.FridgeItemService;

@SpringBootTest
@AutoConfigureMockMvc
class FridgeItemConflictResponseTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtProvider jwtProvider;

	@MockitoBean
	private FridgeItemService fridgeItemService;

	@Test
	void 동시에_고쳐서_저장이_막히면_409를_반환한다() throws Exception {
		given(fridgeItemService.update(anyLong(), anyLong(), any()))
			.willThrow(new ObjectOptimisticLockingFailureException(FridgeItem.class, 1L));

		mockMvc.perform(put("/api/fridge-items/{itemId}", 1)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"두부\", \"version\": 0}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CONCURRENT_MODIFICATION"));
	}

}
