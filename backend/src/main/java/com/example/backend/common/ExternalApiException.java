package com.example.backend.common;

/** 外部API呼出時のエラーを業務エラーへ正規化したもの。 */
public class ExternalApiException extends RuntimeException {

	private final String code;
	private final int httpStatus;
	private final boolean retryable;

	public ExternalApiException(String code, String message, int httpStatus, boolean retryable) {
		super(message);
		this.code = code;
		this.httpStatus = httpStatus;
		this.retryable = retryable;
	}

	public String getCode() {
		return code;
	}

	public int getHttpStatus() {
		return httpStatus;
	}

	public boolean isRetryable() {
		return retryable;
	}
}
