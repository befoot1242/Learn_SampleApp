package com.example.backend.user;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public List<UserDtos.UserResponse> list(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) AppUser.Role role,
			@RequestParam(required = false) AppUser.Status status) {
		return userService.search(keyword, role, status).stream()
				.map(UserDtos.UserResponse::from)
				.toList();
	}

	@PostMapping
	public UserDtos.UserResponse create(@Valid @RequestBody UserDtos.CreateUserRequest request,
			Authentication authentication) {
		return UserDtos.UserResponse.from(userService.create(request, authentication.getName()));
	}

	@PutMapping("/{id}")
	public UserDtos.UserResponse update(@PathVariable Long id,
			@Valid @RequestBody UserDtos.UpdateUserRequest request,
			Authentication authentication) {
		return UserDtos.UserResponse.from(userService.update(id, request, authentication.getName()));
	}

	@PostMapping("/{id}/disable")
	public UserDtos.UserResponse disable(@PathVariable Long id, Authentication authentication) {
		return UserDtos.UserResponse.from(userService.disable(id, authentication.getName()));
	}

	@GetMapping("/{id}/role-history")
	public List<UserDtos.RoleHistoryResponse> roleHistory(@PathVariable Long id) {
		return userService.roleHistories(id).stream()
				.map(UserDtos.RoleHistoryResponse::from)
				.toList();
	}
}
