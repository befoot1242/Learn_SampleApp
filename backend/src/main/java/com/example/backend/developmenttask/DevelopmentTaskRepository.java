package com.example.backend.developmenttask;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DevelopmentTaskRepository extends JpaRepository<DevelopmentTask, String> {
}
