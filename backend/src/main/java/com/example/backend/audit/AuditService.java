package com.example.backend.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

	private static final Logger log = LoggerFactory.getLogger("AUDIT");

	private final AuditLogRepository repository;

	public AuditService(AuditLogRepository repository) {
		this.repository = repository;
	}

	/** 監査ログは業務トランザクションの成否に関わらず残すため独立トランザクションで記録する。 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(String username, String action, String detail, String result) {
		repository.save(AuditLog.of(username, action, detail, result));
		log.info("user={} action={} result={} detail={}", username, action, result, detail);
	}
}
