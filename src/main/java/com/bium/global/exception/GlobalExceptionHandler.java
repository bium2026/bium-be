package com.bium.global.exception;

import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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

	// 잠금을 기다리다 시간이 다 되거나 교착 상태로 DB가 요청을 되돌리면 다른 멤버의 요청과 겹친 것이라,
	// 500 대신 동시 수정과 같은 409로 알려 화면이 새로 불러오게 한다
	@ExceptionHandler(PessimisticLockingFailureException.class)
	public ResponseEntity<ApiResponse<Void>> handlePessimisticLockingFailureException(
		PessimisticLockingFailureException e) {
		log.warn("잠금을 얻지 못해 요청을 되돌렸습니다.", e);
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

	// 주소나 쿼리 파라미터를 숫자·enum 같은 타입으로 바꾸지 못하면 다른 입력 오류와 같은 코드로 준다
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
		HttpStatusCode status, WebRequest request) {
		return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_INPUT));
	}

	// spring.servlet.multipart의 크기 제한을 넘은 사진
	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
		HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ErrorCode errorCode = ErrorCode.IMAGE_TOO_LARGE;
		return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.error(errorCode));
	}

	// 잘못된 JSON, 없는 주소, 지원하지 않는 메서드 같은 Spring MVC 기본 예외도 공통 응답 형식으로 보낸다.
	// 400(형식이 틀린 본문, 숫자 자리의 글자, 빠진 값)은 입력 검증 실패와 같은 INVALID_INPUT으로 알리고,
	// 나머지도 영어 상태 문구 대신 한국어로 알린다
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
		HttpStatusCode statusCode, WebRequest request) {
		HttpStatus status = HttpStatus.valueOf(statusCode.value());
		if (status == HttpStatus.BAD_REQUEST) {
			return ResponseEntity.badRequest()
				.headers(headers)
				.body(ApiResponse.error(ErrorCode.INVALID_INPUT.name(), "요청 형식이 올바르지 않습니다."));
		}
		return ResponseEntity.status(status)
			.headers(headers)
			.body(ApiResponse.error(status.name(), messageOf(status)));
	}

	private String messageOf(HttpStatus status) {
		return switch (status) {
			case NOT_FOUND -> "요청한 주소를 찾을 수 없습니다.";
			case METHOD_NOT_ALLOWED -> "지원하지 않는 요청 방식입니다.";
			case NOT_ACCEPTABLE, UNSUPPORTED_MEDIA_TYPE -> "지원하지 않는 요청 형식입니다.";
			default -> "요청을 처리하지 못했습니다.";
		};
	}

}
