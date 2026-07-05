package com.example.backend.transaction;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import com.example.backend.common.ExternalApiException;

import tools.jackson.databind.ObjectMapper;

import java.util.function.Function;
import java.net.URI;

/**
 * 対向システム(外部API)クライアント。
 * タイムアウト・再試行・エラー正規化をここで一元的に扱う。
 */
@Component
public class ExternalApiClient {

	private static final Logger log = LoggerFactory.getLogger(ExternalApiClient.class);

	private final RestClient restClient;
	private final ExternalApiProperties properties;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public ExternalApiClient(ExternalApiProperties properties) {
		this.properties = properties;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(properties.connectTimeoutMillis());
		factory.setReadTimeout(properties.readTimeoutMillis());
		this.restClient = RestClient.builder()
				.baseUrl(properties.baseUrl())
				.requestFactory(factory)
				.build();
	}

	/** 参照系は冪等なので設定回数まで再試行する。 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getWithRetry(Function<UriBuilder, URI> uriFunction) {
		ResourceAccessException lastException = null;
		for (int attempt = 0; attempt <= properties.maxRetries(); attempt++) {
			try {
				return restClient.get()
						.uri(uriFunction)
						.retrieve()
						.body(Map.class);
			} catch (ResourceAccessException e) {
				lastException = e;
				log.warn("外部API呼出失敗 (attempt {}/{}): {}", attempt + 1,
						properties.maxRetries() + 1, e.getMessage());
			} catch (HttpStatusCodeException e) {
				throw normalize(e);
			}
		}
		throw new ExternalApiException("EXTERNAL_UNAVAILABLE",
				"外部システムに接続できません。しばらくしてから再試行してください", 503, true);
	}

	/** 更新系は自動再試行しない(重複防止はリクエストIDで担保)。 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> post(String path, Object body) {
		try {
			return restClient.post()
					.uri(path)
					.body(body)
					.retrieve()
					.body(Map.class);
		} catch (ResourceAccessException e) {
			log.warn("外部API登録呼出失敗: {}", e.getMessage());
			throw new ExternalApiException("EXTERNAL_TIMEOUT",
					"外部システムへの登録要求がタイムアウトしました。取引一覧で状態を確認してください", 504, false);
		} catch (HttpStatusCodeException e) {
			throw normalize(e);
		}
	}

	/** 外部システムのエラーレスポンスを自システムの業務エラー形式へ正規化する。 */
	private ExternalApiException normalize(HttpStatusCodeException e) {
		String externalMessage = extractMessage(e.getResponseBodyAsString());
		int status = e.getStatusCode().value();
		if (status == 404) {
			return new ExternalApiException("NOT_FOUND",
					externalMessage != null ? externalMessage : "対象のデータが見つかりません", 404, false);
		}
		if (status == 422) {
			return new ExternalApiException("BUSINESS_ERROR",
					externalMessage != null ? externalMessage : "外部システムで業務エラーが発生しました", 422, false);
		}
		if (status == 400) {
			return new ExternalApiException("INVALID_REQUEST",
					externalMessage != null ? externalMessage : "外部システムへの要求内容が不正です", 400, false);
		}
		return new ExternalApiException("EXTERNAL_ERROR",
				"外部システムでエラーが発生しました。しばらくしてから再試行してください", 502, true);
	}

	private String extractMessage(String responseBody) {
		try {
			Map<String, Object> parsed = objectMapper.readValue(responseBody, Map.class);
			Object message = parsed.get("message");
			return message != null ? message.toString() : null;
		} catch (Exception ignored) {
			return null;
		}
	}
}
