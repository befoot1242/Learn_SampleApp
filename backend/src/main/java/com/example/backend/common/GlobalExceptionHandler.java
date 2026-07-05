package com.example.backend.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("code", e.getCode(), "message", e.getMessage()));
	}

	@ExceptionHandler(ExternalApiException.class)
	public ResponseEntity<Map<String, Object>> handleExternalApi(ExternalApiException e) {
		return ResponseEntity.status(e.getHttpStatus())
				.body(Map.of(
						"code", e.getCode(),
						"message", e.getMessage(),
						"retryable", e.isRetryable()));
	}

	@ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> handleUnreadable(
			org.springframework.http.converter.HttpMessageNotReadableException e) {
		return ResponseEntity.badRequest()
				.body(Map.of("code", "INVALID_BODY", "message", "リクエスト形式が不正です"));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors()
				.forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
		return ResponseEntity.badRequest()
				.body(Map.of(
						"code", "VALIDATION_ERROR",
						"message", "入力内容に誤りがあります",
						"fieldErrors", fieldErrors));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
		log.error("Unexpected error", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("code", "INTERNAL_ERROR", "message", "サーバ内部でエラーが発生しました"));
	}
}
