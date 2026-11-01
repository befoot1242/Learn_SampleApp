package com.example.backend.audit;

public record AuditSuccessEvent(String username, String action, String detail) {
}
