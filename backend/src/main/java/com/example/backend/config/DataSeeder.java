package com.example.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.backend.user.AppUser;
import com.example.backend.user.AppUserRepository;

/** 初回起動時にデモ用ユーザを投入する。 */
@Component
public class DataSeeder implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

	private final AppUserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public DataSeeder(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(String... args) {
		if (userRepository.count() > 0) {
			return;
		}
		seedUser("admin", "管理者 太郎", "admin@example.com", AppUser.Role.ADMIN, "password123");
		seedUser("user1", "一般 花子", "user1@example.com", AppUser.Role.USER, "password123");
		log.info("デモユーザを作成しました: admin / user1 (パスワード: password123)");
	}

	private void seedUser(String username, String displayName, String email,
			AppUser.Role role, String rawPassword) {
		AppUser user = new AppUser();
		user.setUsername(username);
		user.setDisplayName(displayName);
		user.setEmail(email);
		user.setRole(role);
		user.setStatus(AppUser.Status.ACTIVE);
		user.setPasswordHash(passwordEncoder.encode(rawPassword));
		userRepository.save(user);
	}
}
