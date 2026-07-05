package com.example.backend.user;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleHistoryRepository extends JpaRepository<RoleHistory, Long> {

	List<RoleHistory> findByUserIdOrderByChangedAtDesc(Long userId);
}
