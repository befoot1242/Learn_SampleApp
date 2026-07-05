package com.example.backend.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.audit.AuditService;
import com.example.backend.user.AppUser;
import com.example.backend.user.AppUserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final AppUserRepository userRepository;
	private final AuditService auditService;

	public AuthController(AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository,
			AppUserRepository userRepository,
			AuditService auditService) {
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.userRepository = userRepository;
		this.auditService = auditService;
	}

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest,
			HttpServletRequest request, HttpServletResponse response) {
		try {
			Authentication authentication = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(loginRequest.username(), loginRequest.password()));

			// セッション固定化攻撃対策としてセッションIDを再生成する
			request.getSession(true);
			request.changeSessionId();

			SecurityContext context = SecurityContextHolder.createEmptyContext();
			context.setAuthentication(authentication);
			SecurityContextHolder.setContext(context);
			securityContextRepository.saveContext(context, request, response);

			auditService.record(loginRequest.username(), "LOGIN", "ログイン", "SUCCESS");
			return ResponseEntity.ok(currentUserBody(authentication.getName()));
		} catch (DisabledException e) {
			auditService.record(loginRequest.username(), "LOGIN", "無効化ユーザによる試行", "FAILURE");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("code", "USER_DISABLED", "message", "このユーザは無効化されています"));
		} catch (AuthenticationException e) {
			auditService.record(loginRequest.username(), "LOGIN", "認証失敗", "FAILURE");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("code", "LOGIN_FAILED", "message", "ユーザIDまたはパスワードが正しくありません"));
		}
	}

	@PostMapping("/logout")
	public ResponseEntity<?> logout(HttpServletRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String username = authentication != null ? authentication.getName() : "anonymous";
		if (request.getSession(false) != null) {
			request.getSession(false).invalidate();
		}
		SecurityContextHolder.clearContext();
		auditService.record(username, "LOGOUT", "ログアウト", "SUCCESS");
		return ResponseEntity.ok(Map.of("message", "ログアウトしました"));
	}

	/** 未認証でも200で返し、フロント側で authenticated フラグを見て分岐する。 */
	@GetMapping("/me")
	public ResponseEntity<?> me(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()
				|| "anonymousUser".equals(authentication.getPrincipal())) {
			return ResponseEntity.ok(Map.of("authenticated", false));
		}
		return ResponseEntity.ok(currentUserBody(authentication.getName()));
	}

	private Map<String, Object> currentUserBody(String username) {
		AppUser user = userRepository.findByUsername(username).orElseThrow();
		return Map.of(
				"authenticated", true,
				"username", user.getUsername(),
				"displayName", user.getDisplayName(),
				"role", user.getRole().name());
	}
}
