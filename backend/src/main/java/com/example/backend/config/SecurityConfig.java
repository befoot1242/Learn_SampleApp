package com.example.backend.config;

import java.io.IOException;
import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf
						.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
						.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
						.ignoringRequestMatchers("/h2-console/**"))
				.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
				.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/auth/login", "/api/auth/me").permitAll()
						.requestMatchers("/h2-console/**").permitAll()
						.requestMatchers("/api/users/**").hasRole("ADMIN")
						.requestMatchers("/api/**").authenticated()
						.anyRequest().denyAll())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, authException) -> writeJsonError(
								response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "認証が必要です"))
						.accessDeniedHandler((request, response, accessDeniedException) -> writeJsonError(
								response, HttpStatus.FORBIDDEN, "FORBIDDEN", "この操作を行う権限がありません")))
				.logout(logout -> logout.disable())
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable());
		return http.build();
	}

	private static void writeJsonError(HttpServletResponse response, HttpStatus status,
			String code, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	@Bean
	public SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/**
	 * SPA向けCSRFトークンハンドラ(Spring Securityリファレンスの推奨実装)。
	 * ヘッダ経由のトークンは平文で検証し、レスポンスにはXORエンコード済みを使う。
	 */
	static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
		private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
		private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response,
				Supplier<CsrfToken> csrfToken) {
			this.xor.handle(request, response, csrfToken);
			csrfToken.get();
		}

		@Override
		public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
			String headerValue = request.getHeader(csrfToken.getHeaderName());
			return (StringUtils.hasText(headerValue) ? this.plain : this.xor)
					.resolveCsrfTokenValue(request, csrfToken);
		}
	}

	/** CSRFトークンのCookieを確実にレスポンスへ書き出すためのフィルタ。 */
	static final class CsrfCookieFilter extends OncePerRequestFilter {
		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
				FilterChain filterChain) throws ServletException, IOException {
			CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
			if (csrfToken != null) {
				csrfToken.getToken();
			}
			filterChain.doFilter(request, response);
		}
	}
}
