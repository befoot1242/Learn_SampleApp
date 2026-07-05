package com.example.backend.user;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** ロール割当変更の履歴。 */
@Entity
@Table(name = "role_history")
public class RoleHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private AppUser.Role oldRole;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AppUser.Role newRole;

	@Column(nullable = false, length = 50)
	private String changedBy;

	@Column(nullable = false, updatable = false)
	private LocalDateTime changedAt;

	@PrePersist
	void onCreate() {
		changedAt = LocalDateTime.now();
	}

	public static RoleHistory of(Long userId, AppUser.Role oldRole, AppUser.Role newRole, String changedBy) {
		RoleHistory h = new RoleHistory();
		h.userId = userId;
		h.oldRole = oldRole;
		h.newRole = newRole;
		h.changedBy = changedBy;
		return h;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public AppUser.Role getOldRole() {
		return oldRole;
	}

	public AppUser.Role getNewRole() {
		return newRole;
	}

	public String getChangedBy() {
		return changedBy;
	}

	public LocalDateTime getChangedAt() {
		return changedAt;
	}
}
