package com.example.backend.user;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.audit.AuditService;
import com.example.backend.common.BusinessException;

@Service
public class UserService {

	private final AppUserRepository userRepository;
	private final RoleHistoryRepository roleHistoryRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuditService auditService;

	public UserService(AppUserRepository userRepository,
			RoleHistoryRepository roleHistoryRepository,
			PasswordEncoder passwordEncoder,
			AuditService auditService) {
		this.userRepository = userRepository;
		this.roleHistoryRepository = roleHistoryRepository;
		this.passwordEncoder = passwordEncoder;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<AppUser> search(String keyword, AppUser.Role role, AppUser.Status status) {
		return userRepository.search(keyword == null ? "" : keyword.trim(), role, status);
	}

	@Transactional
	public AppUser create(UserDtos.CreateUserRequest request, String operator) {
		userRepository.findByUsername(request.username()).ifPresent(u -> {
			throw new BusinessException("DUPLICATE_USER", "ユーザID「" + request.username() + "」は既に使用されています");
		});
		AppUser user = new AppUser();
		user.setUsername(request.username());
		user.setDisplayName(request.displayName());
		user.setEmail(request.email());
		user.setRole(request.role());
		user.setStatus(AppUser.Status.ACTIVE);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		AppUser saved = userRepository.save(user);
		roleHistoryRepository.save(RoleHistory.of(saved.getId(), null, saved.getRole(), operator));
		auditService.record(operator, "USER_CREATE", "ユーザ作成: " + saved.getUsername(), "SUCCESS");
		return saved;
	}

	@Transactional
	public AppUser update(Long id, UserDtos.UpdateUserRequest request, String operator) {
		AppUser user = userRepository.findById(id)
				.orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "指定されたユーザが見つかりません"));
		if (user.getUsername().equals(operator) && request.status() == AppUser.Status.DISABLED) {
			throw new BusinessException("SELF_DISABLE", "自分自身を無効化することはできません");
		}
		if (user.getRole() != request.role()) {
			roleHistoryRepository.save(RoleHistory.of(user.getId(), user.getRole(), request.role(), operator));
		}
		user.setDisplayName(request.displayName());
		user.setEmail(request.email());
		user.setRole(request.role());
		user.setStatus(request.status());
		auditService.record(operator, "USER_UPDATE", "ユーザ更新: " + user.getUsername(), "SUCCESS");
		return user;
	}

	@Transactional
	public AppUser disable(Long id, String operator) {
		AppUser user = userRepository.findById(id)
				.orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "指定されたユーザが見つかりません"));
		if (user.getUsername().equals(operator)) {
			throw new BusinessException("SELF_DISABLE", "自分自身を無効化することはできません");
		}
		user.setStatus(AppUser.Status.DISABLED);
		auditService.record(operator, "USER_DISABLE", "ユーザ無効化: " + user.getUsername(), "SUCCESS");
		return user;
	}

	@Transactional(readOnly = true)
	public List<RoleHistory> roleHistories(Long userId) {
		return roleHistoryRepository.findByUserIdOrderByChangedAtDesc(userId);
	}
}
