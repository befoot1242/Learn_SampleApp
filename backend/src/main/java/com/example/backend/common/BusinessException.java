package com.example.backend.common;

/** 業務エラー(入力内容や状態に起因するエラー)。HTTP 409 で返す。 */
public class BusinessException extends RuntimeException {

	private final String code;

	public BusinessException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
