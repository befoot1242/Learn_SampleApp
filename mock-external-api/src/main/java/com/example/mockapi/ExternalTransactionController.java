package com.example.mockapi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 対向システム(外部API)を模したエンドポイント。
 *
 * 動作確認用の特殊ルール:
 * - partnerName に「FAIL」を含む登録リクエストは 500 を返す(障害シミュレーション)
 * - partnerName に「NG」を含む登録リクエストは 422 を返す(業務エラーシミュレーション)
 * - 同一 requestId の再送は初回と同じ結果を返す(冪等性)
 */
@RestController
@RequestMapping("/external/transactions")
public class ExternalTransactionController {

	private final TransactionStore store;
	private final Map<String, Map<String, Object>> processedRequests = new ConcurrentHashMap<>();

	public ExternalTransactionController(TransactionStore store) {
		this.store = store;
	}

	@GetMapping
	public Map<String, Object> list(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String partnerName,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		List<TransactionStore.Transaction> all = store.search(dateFrom, dateTo, status, partnerName);
		int safeSize = Math.max(1, Math.min(size, 100));
		int totalPages = Math.max(1, (all.size() + safeSize - 1) / safeSize);
		int safePage = Math.max(0, Math.min(page, totalPages - 1));
		int from = safePage * safeSize;
		int to = Math.min(from + safeSize, all.size());
		List<Map<String, Object>> content = all.subList(Math.min(from, all.size()), to).stream()
				.map(this::toSummary)
				.toList();
		return Map.of(
				"content", content,
				"page", safePage,
				"size", safeSize,
				"totalElements", all.size(),
				"totalPages", totalPages);
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> detail(@PathVariable String id) {
		TransactionStore.Transaction tx = store.find(id);
		if (tx == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("code", "NOT_FOUND", "message", "指定された取引が見つかりません: " + id));
		}
		return ResponseEntity.ok(toDetail(tx));
	}

	public record RegisterRequest(String requestId, String partnerName, BigDecimal amount,
			LocalDate transactionDate, String note) {
	}

	@PostMapping
	public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
		if (request.requestId() == null || request.requestId().isBlank()) {
			return ResponseEntity.badRequest()
					.body(Map.of("code", "INVALID_REQUEST", "message", "requestId は必須です"));
		}
		Map<String, Object> previous = processedRequests.get(request.requestId());
		if (previous != null) {
			return ResponseEntity.ok(previous);
		}
		if (request.partnerName() != null && request.partnerName().contains("FAIL")) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("code", "SYSTEM_ERROR", "message", "対向システムで内部エラーが発生しました"));
		}
		if (request.partnerName() != null && request.partnerName().contains("NG")) {
			return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
					.body(Map.of("code", "BUSINESS_ERROR", "message", "取引先が取引停止中のため登録できません"));
		}
		if (request.partnerName() == null || request.partnerName().isBlank()
				|| request.amount() == null || request.transactionDate() == null) {
			return ResponseEntity.badRequest()
					.body(Map.of("code", "INVALID_REQUEST", "message", "取引先・金額・取引日は必須です"));
		}
		TransactionStore.Transaction tx = store.register(
				request.partnerName(), request.amount(), request.transactionDate(), request.note());
		Map<String, Object> result = Map.of(
				"receiptNumber", "RCPT-" + tx.id,
				"transactionId", tx.id,
				"status", tx.status);
		processedRequests.put(request.requestId(), result);
		return ResponseEntity.ok(result);
	}

	private Map<String, Object> toSummary(TransactionStore.Transaction tx) {
		return Map.of(
				"transactionId", tx.id,
				"partnerName", tx.partnerName,
				"amount", tx.amount,
				"transactionDate", tx.transactionDate.toString(),
				"status", tx.status);
	}

	private Map<String, Object> toDetail(TransactionStore.Transaction tx) {
		return Map.of(
				"transactionId", tx.id,
				"partnerName", tx.partnerName,
				"amount", tx.amount,
				"transactionDate", tx.transactionDate.toString(),
				"status", tx.status,
				"note", tx.note,
				"lines", tx.lines.stream().map(l -> Map.of(
						"lineNo", l.lineNo(),
						"itemName", l.itemName(),
						"quantity", l.quantity(),
						"unitPrice", l.unitPrice())).toList(),
				"histories", tx.histories.stream().map(h -> Map.of(
						"occurredAt", h.occurredAt().toString(),
						"event", h.event(),
						"operator", h.operator())).toList());
	}
}
