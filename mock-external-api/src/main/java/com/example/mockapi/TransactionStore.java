package com.example.mockapi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * 外部システム側の取引データを模したインメモリストア。
 * 再起動でリセットされる前提(モック用途)。
 */
@Component
public class TransactionStore {

	public record TransactionLine(int lineNo, String itemName, int quantity, BigDecimal unitPrice) {
	}

	public record TransactionHistory(LocalDateTime occurredAt, String event, String operator) {
	}

	public static final class Transaction {
		public final String id;
		public final String partnerName;
		public final BigDecimal amount;
		public final LocalDate transactionDate;
		public final String status;
		public final String note;
		public final List<TransactionLine> lines;
		public final List<TransactionHistory> histories;

		Transaction(String id, String partnerName, BigDecimal amount, LocalDate transactionDate,
				String status, String note, List<TransactionLine> lines, List<TransactionHistory> histories) {
			this.id = id;
			this.partnerName = partnerName;
			this.amount = amount;
			this.transactionDate = transactionDate;
			this.status = status;
			this.note = note;
			this.lines = lines;
			this.histories = histories;
		}
	}

	private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
	private final AtomicLong sequence = new AtomicLong(1000);

	public TransactionStore() {
		seed();
	}

	private void seed() {
		String[] partners = { "山田商事", "鈴木物産", "佐藤工業", "田中興産", "高橋製作所" };
		String[] statuses = { "COMPLETED", "PENDING", "CANCELLED" };
		for (int i = 0; i < 35; i++) {
			String id = "TX-" + sequence.getAndIncrement();
			String partner = partners[i % partners.length];
			String status = statuses[i % statuses.length];
			BigDecimal amount = BigDecimal.valueOf((i + 1) * 12500L);
			LocalDate date = LocalDate.of(2026, 6, 1).plusDays(i);
			List<TransactionLine> lines = List.of(
					new TransactionLine(1, "商品A-" + (i + 1), 2, BigDecimal.valueOf(5000)),
					new TransactionLine(2, "商品B-" + (i + 1), 1, amount.subtract(BigDecimal.valueOf(10000))));
			List<TransactionHistory> histories = new ArrayList<>();
			histories.add(new TransactionHistory(date.atTime(9, 0), "REGISTERED", "external-system"));
			if (!"PENDING".equals(status)) {
				histories.add(new TransactionHistory(date.atTime(15, 30),
						"COMPLETED".equals(status) ? "APPROVED" : "CANCELLED", "external-system"));
			}
			transactions.put(id, new Transaction(id, partner, amount, date, status, "初期データ", lines, histories));
		}
	}

	public List<Transaction> search(LocalDate dateFrom, LocalDate dateTo, String status, String partnerName) {
		return transactions.values().stream()
				.filter(t -> dateFrom == null || !t.transactionDate.isBefore(dateFrom))
				.filter(t -> dateTo == null || !t.transactionDate.isAfter(dateTo))
				.filter(t -> status == null || status.isBlank() || t.status.equals(status))
				.filter(t -> partnerName == null || partnerName.isBlank() || t.partnerName.contains(partnerName))
				.sorted(Comparator.comparing((Transaction t) -> t.transactionDate).reversed()
						.thenComparing(t -> t.id, Comparator.reverseOrder()))
				.toList();
	}

	public Transaction find(String id) {
		return transactions.get(id);
	}

	public Transaction register(String partnerName, BigDecimal amount, LocalDate transactionDate, String note) {
		String id = "TX-" + sequence.getAndIncrement();
		List<TransactionHistory> histories = List.of(
				new TransactionHistory(LocalDateTime.now(), "REGISTERED", "bff"));
		Transaction tx = new Transaction(id, partnerName, amount, transactionDate, "PENDING",
				note == null ? "" : note, List.of(), histories);
		transactions.put(id, tx);
		return tx;
	}
}
