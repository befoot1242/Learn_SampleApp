package com.example.backend.auth;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.backend.user.AppUser;
import com.example.backend.user.AppUserRepository;

@Service
public class AppUserDetailsService implements UserDetailsService {

	private final AppUserRepository repository;

	public AppUserDetailsService(AppUserRepository repository) {
		this.repository = repository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		AppUser user = repository.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("ユーザが見つかりません: " + username));
		return User.builder()
				.username(user.getUsername())
				.password(user.getPasswordHash())
				.authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
				.disabled(user.getStatus() == AppUser.Status.DISABLED)
				.build();
	}
}
