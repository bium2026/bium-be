package com.bium.global.exception;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.bium.global.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	// 다시 시도할 수 있는 시각을 Retry-After 헤더로 알려 준다
	@ExceptionHandler(TooManyRequestsException.class)
	public ResponseEntity<ApiResponse<Void>> handleTooManyRequestsException(TooManyRequestsException e) {
		ErrorCode errorCode = e.getErrorCode();
		return ResponseEntity.status(errorCode.getStatus())
			.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
			.body(ApiResponse.error(errorCode));
	}

	// 같은 데이터를 두 사람이 동시에 고치면 나중에 저장한 쪽을 막는다 (@Version)
	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ApiResponse<Void>> handleOptimisticLockingFailureException(
		OptimisticLockingFailureException e) {
		ErrorCode errorCode = ErrorCode.CONCURRENT_MODIFICATION;
		return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode));
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
		ErrorCode errorCode = e.getErrorCode();
		return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception e) {
		log.error("처리하지 못한 예외가 발생했습니다.", e);
		ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
		return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
		HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String message = ex.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(FieldError::getDefaultMessage)
			.orElse(ErrorCode.INVALID_INPUT.getMessage());
		return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_INPUT.name(), message));
	}

	// 잘못된 JSON, 없는 주소, 지원하지 않는 메서드 같은 Spring MVC 기본 예외도 공통 응답 형식으로 보낸다
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
		HttpStatusCode statusCode, WebRequest request) {
		HttpStatus status = HttpStatus.valueOf(statusCode.value());
		return ResponseEntity.status(status)
			.headers(headers)
			.body(ApiResponse.error(status.name(), status.getReasonPhrase()));
	}

}
