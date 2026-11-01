package com.example.backend.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.audit.AuditSuccessEvent;
import com.example.backend.common.BusinessException;

@Service
public class ApplicationService {

	private final ApplicationRequestRepository repository;
	private final ApplicationEventPublisher eventPublisher;

	public ApplicationService(ApplicationRequestRepository repository, ApplicationEventPublisher eventPublisher) {
		this.repository = repository;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public ApplicationRequest create(ApplicationDtos.CreateApplicationRequest request, String operator) {
		if (request.endTime().isBefore(request.startTime())) {
			throw new BusinessException("INVALID_TIME_RANGE", "終了時刻は開始時刻以降を指定してください");
		}
		if (request.periodEnd().isBefore(request.periodStart())) {
			throw new BusinessException("INVALID_PERIOD", "利用終了日は開始日以降を指定してください");
		}

		ApplicationRequest application = new ApplicationRequest();
		application.setApplicationType(request.applicationType().trim());
		application.setNameKanji(request.nameKanji().trim());
		application.setNameKana(request.nameKana().trim());
		application.setNameAlphabet(request.nameAlphabet().trim());
		application.setEmployeeNumber(request.employeeNumber().trim());
		application.setEmail(request.email().trim());
		application.setPhoneNumber(request.phoneNumber().trim());
		application.setWebsiteUrl(blankToNull(request.websiteUrl()));
		application.setBirthDate(request.birthDate());
		application.setDesiredDate(request.desiredDate());
		application.setStartTime(request.startTime());
		application.setEndTime(request.endTime());
		application.setNotificationAt(request.notificationAt());
		application.setPeriodStart(request.periodStart());
		application.setPeriodEnd(request.periodEnd());
		application.setPostalCode(request.postalCode().trim());
		application.setPrefecture(request.prefecture().trim());
		application.setAddress(request.address().trim());
		application.setBuildingName(blankToNull(request.buildingName()));
		application.setQuantity(request.quantity());
		application.setAmount(request.amount());
		application.setPercentage(request.percentage());
		application.setRating(request.rating());
		application.setGender(request.gender().trim());
		application.setLanguages(request.languages());
		application.setContactMethod(request.contactMethod().trim());
		application.setPriority(request.priority());
		application.setThemeColor(request.themeColor());
		application.setAttachmentFileName(blankToNull(request.attachmentFileName()));
		application.setSubject(request.subject().trim());
		application.setContent(request.content().trim());
		application.setConfidentiality(request.confidentiality().trim());
		application.setTermsAgreed(request.termsAgreed());
		application.setMarketingOptIn(request.marketingOptIn());
		application.setCreatedBy(operator);

		ApplicationRequest saved = repository.save(application);
		eventPublisher.publishEvent(new AuditSuccessEvent(operator, "APPLICATION_CREATE", "申請登録: id=" + saved.getId()));
		return saved;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
