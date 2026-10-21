package com.bium.global.response;

import com.bium.global.exception.ErrorCode;

public record ApiResponse<T>(boolean success, T data, ErrorDetail error) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static ApiResponse<Void> error(ErrorCode errorCode) {
		return error(errorCode.name(), errorCode.getMessage());
	}

	public static ApiResponse<Void> error(String code, String message) {
		return new ApiResponse<>(false, null, new ErrorDetail(code, message));
	}

	public record ErrorDetail(String code, String message) {
	}

}
