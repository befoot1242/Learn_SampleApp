package com.example.backend.audit;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** 認証・取引操作の監査ログ。 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private LocalDateTime occurredAt;

	@Column(nullable = false, length = 50)
	private String username;

	@Column(nullable = false, length = 50)
	private String action;

	@Column(length = 500)
	private String detail;

	@Column(nullable = false, length = 20)
	private String result;

	@PrePersist
	void onCreate() {
		occurredAt = LocalDateTime.now();
	}

	public static AuditLog of(String username, String action, String detail, String result) {
		AuditLog log = new AuditLog();
		log.username = username;
		log.action = action;
		log.detail = detail;
		log.result = result;
		return log;
	}

	public Long getId() {
		return id;
	}

	public LocalDateTime getOccurredAt() {
		return occurredAt;
	}

	public String getUsername() {
		return username;
	}

	public String getAction() {
		return action;
	}

	public String getDetail() {
		return detail;
	}

	public String getResult() {
		return result;
	}
}
