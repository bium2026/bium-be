package com.bium.domain.fridge.dto;

import java.time.LocalDateTime;

import com.bium.domain.fridge.entity.FridgeInvite;

public record FridgeInviteResponse(String code, LocalDateTime expiresAt) {

	public static FridgeInviteResponse from(FridgeInvite invite) {
		return new FridgeInviteResponse(invite.getCode(), invite.getExpiresAt());
	}

}
