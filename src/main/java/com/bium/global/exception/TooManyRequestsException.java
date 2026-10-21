package com.bium.global.exception;

import lombok.Getter;

@Getter
public class TooManyRequestsException extends BusinessException {

	private final long retryAfterSeconds;

	public TooManyRequestsException(long retryAfterSeconds) {
		super(ErrorCode.TOO_MANY_REQUESTS);
		this.retryAfterSeconds = retryAfterSeconds;
	}

}
