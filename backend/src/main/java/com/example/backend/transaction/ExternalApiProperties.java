package com.example.backend.transaction;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 外部API接続設定。application.properties の external.api.* で一元管理する。 */
@ConfigurationProperties(prefix = "external.api")
public record ExternalApiProperties(
		String baseUrl,
		int connectTimeoutMillis,
		int readTimeoutMillis,
		int maxRetries) {
}
