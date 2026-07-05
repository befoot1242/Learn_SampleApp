package com.example.backend.user;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UserDtos {

	public record UserResponse(
			Long id,
			String username,
			String displayName,
			String email,
			String status,
			String role,
			LocalDateTime createdAt,
			LocalDateTime updatedAt) {

		public static UserResponse from(AppUser user) {
			return new UserResponse(
					user.getId(),
					user.getUsername(),
					user.getDisplayName(),
					user.getEmail(),
					user.getStatus().name(),
					user.getRole().name(),
					user.getCreatedAt(),
					user.getUpdatedAt());
		}
	}

	public record CreateUserRequest(
			@NotBlank @Size(max = 50) @Pattern(regexp = "[a-zA-Z0-9_.-]+", message = "ユーザIDは半角英数字と記号(_.-)のみ使用できます") String username,
			@NotBlank @Size(max = 100) String displayName,
			@NotBlank @Email @Size(max = 255) String email,
			@NotNull AppUser.Role role,
			@NotBlank @Size(min = 8, max = 72) String password) {
	}

	public record UpdateUserRequest(
			@NotBlank @Size(max = 100) String displayName,
			@NotBlank @Email @Size(max = 255) String email,
			@NotNull AppUser.Role role,
			@NotNull AppUser.Status status) {
	}

	public record RoleHistoryResponse(
			Long id,
			String oldRole,
			String newRole,
			String changedBy,
			LocalDateTime changedAt) {

		public static RoleHistoryResponse from(RoleHistory history) {
			return new RoleHistoryResponse(
					history.getId(),
					history.getOldRole() != null ? history.getOldRole().name() : null,
					history.getNewRole().name(),
					history.getChangedBy(),
					history.getChangedAt());
		}
	}
}
