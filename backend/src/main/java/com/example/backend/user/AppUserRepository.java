package com.example.backend.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	Optional<AppUser> findByUsername(String username);

	@Query("""
			select u from AppUser u
			where (:keyword = '' or lower(u.username) like lower(concat('%', :keyword, '%'))
				or lower(u.displayName) like lower(concat('%', :keyword, '%'))
				or lower(u.email) like lower(concat('%', :keyword, '%')))
			and (:role is null or u.role = :role)
			and (:status is null or u.status = :status)
			order by u.id
			""")
	List<AppUser> search(@Param("keyword") String keyword,
			@Param("role") AppUser.Role role,
			@Param("status") AppUser.Status status);
}
