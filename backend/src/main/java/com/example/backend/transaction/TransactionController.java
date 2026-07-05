package com.example.backend.transaction;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.audit.AuditService;
import com.example.backend.common.ExternalApiException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 取引参照・登録のBFFエンドポイント。外部APIへの中継とエラー正規化を担う。 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

	private final ExternalApiClient externalApiClient;
	private final AuditService auditService;

	public TransactionController(ExternalApiClient externalApiClient, AuditService auditService) {
		this.externalApiClient = externalApiClient;
		this.auditService = auditService;
	}

	@GetMapping
	public Map<String, Object> list(
			@RequestParam(required = false) String dateFrom,
			@RequestParam(required = false) String dateTo,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String partnerName,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return externalApiClient.getWithRetry(uriBuilder -> uriBuilder
				.path("/external/transactions")
				.queryParamIfPresent("dateFrom", blankToEmpty(dateFrom))
				.queryParamIfPresent("dateTo", blankToEmpty(dateTo))
				.queryParamIfPresent("status", blankToEmpty(status))
				.queryParamIfPresent("partnerName", blankToEmpty(partnerName))
				.queryParam("page", page)
				.queryParam("size", size)
				.build());
	}

	@GetMapping("/{id}")
	public Map<String, Object> detail(@PathVariable String id) {
		return externalApiClient.getWithRetry(uriBuilder -> uriBuilder
				.path("/external/transactions/{id}")
				.build(id));
	}

	public record RegisterRequest(
			@NotBlank String requestId,
			@NotBlank @Size(max = 100) String partnerName,
			@NotNull @DecimalMin(value = "1", message = "金額は1以上で入力してください") BigDecimal amount,
			@NotBlank String transactionDate,
			@Size(max = 500) String note) {
	}

	@PostMapping
	public Map<String, Object> register(@Valid @RequestBody RegisterRequest request,
			Authentication authentication) {
		String username = authentication.getName();
		try {
			Map<String, Object> result = externalApiClient.post("/external/transactions", Map.of(
					"requestId", request.requestId(),
					"partnerName", request.partnerName(),
					"amount", request.amount(),
					"transactionDate", request.transactionDate(),
					"note", request.note() == null ? "" : request.note()));
			auditService.record(username, "TX_REGISTER",
					"取引登録: requestId=" + request.requestId() + " receipt=" + result.get("receiptNumber"),
					"SUCCESS");
			return result;
		} catch (ExternalApiException e) {
			auditService.record(username, "TX_REGISTER",
					"取引登録失敗: requestId=" + request.requestId() + " code=" + e.getCode(), "FAILURE");
			throw e;
		}
	}

	private static java.util.Optional<String> blankToEmpty(String value) {
		return (value == null || value.isBlank()) ? java.util.Optional.empty() : java.util.Optional.of(value);
	}
}
