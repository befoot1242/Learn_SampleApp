package com.example.backend.audit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuditSuccessEventListener {

	private final AuditService auditService;

	public AuditSuccessEventListener(AuditService auditService) {
		this.auditService = auditService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void record(AuditSuccessEvent event) {
		auditService.record(event.username(), event.action(), event.detail(), "SUCCESS");
	}
}
