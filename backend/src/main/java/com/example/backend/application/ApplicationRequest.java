package com.example.backend.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "application_request")
public class ApplicationRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 30)
	private String applicationType;

	@Column(nullable = false, length = 100)
	private String nameKanji;

	@Column(nullable = false, length = 100)
	private String nameKana;

	@Column(nullable = false, length = 100)
	private String nameAlphabet;

	@Column(nullable = false, length = 12)
	private String employeeNumber;

	@Column(nullable = false, length = 255)
	private String email;

	@Column(nullable = false, length = 20)
	private String phoneNumber;

	@Column(length = 255)
	private String websiteUrl;

	@Column(nullable = false)
	private LocalDate birthDate;

	@Column(nullable = false)
	private LocalDate desiredDate;

	@Column(nullable = false)
	private LocalTime startTime;

	@Column(nullable = false)
	private LocalTime endTime;

	private LocalDateTime notificationAt;

	@Column(nullable = false)
	private LocalDate periodStart;

	@Column(nullable = false)
	private LocalDate periodEnd;

	@Column(nullable = false, length = 8)
	private String postalCode;

	@Column(nullable = false, length = 10)
	private String prefecture;

	@Column(nullable = false, length = 200)
	private String address;

	@Column(length = 200)
	private String buildingName;

	@Column(nullable = false)
	private Integer quantity;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false)
	private Integer percentage;

	@Column(nullable = false)
	private Integer rating;

	@Column(nullable = false, length = 20)
	private String gender;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "application_request_language", joinColumns = @JoinColumn(name = "application_id"))
	@Column(name = "language", nullable = false, length = 30)
	private List<String> languages = new ArrayList<>();

	@Column(nullable = false, length = 20)
	private String contactMethod;

	@Column(nullable = false)
	private Integer priority;

	@Column(nullable = false, length = 7)
	private String themeColor;

	@Column(length = 255)
	private String attachmentFileName;

	@Column(nullable = false, length = 200)
	private String subject;

	@Column(nullable = false, length = 2000)
	private String content;

	@Column(nullable = false, length = 20)
	private String confidentiality;

	@Column(nullable = false)
	private boolean termsAgreed;

	@Column(nullable = false)
	private boolean marketingOptIn;

	@Column(nullable = false, length = 20)
	private String status = "RECEIVED";

	@Column(nullable = false, updatable = false, length = 50)
	private String createdBy;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public String getApplicationType() { return applicationType; }
	public void setApplicationType(String value) { applicationType = value; }
	public String getNameKanji() { return nameKanji; }
	public void setNameKanji(String value) { nameKanji = value; }
	public String getNameKana() { return nameKana; }
	public void setNameKana(String value) { nameKana = value; }
	public String getNameAlphabet() { return nameAlphabet; }
	public void setNameAlphabet(String value) { nameAlphabet = value; }
	public String getEmployeeNumber() { return employeeNumber; }
	public void setEmployeeNumber(String value) { employeeNumber = value; }
	public String getEmail() { return email; }
	public void setEmail(String value) { email = value; }
	public String getPhoneNumber() { return phoneNumber; }
	public void setPhoneNumber(String value) { phoneNumber = value; }
	public String getWebsiteUrl() { return websiteUrl; }
	public void setWebsiteUrl(String value) { websiteUrl = value; }
	public LocalDate getBirthDate() { return birthDate; }
	public void setBirthDate(LocalDate value) { birthDate = value; }
	public LocalDate getDesiredDate() { return desiredDate; }
	public void setDesiredDate(LocalDate value) { desiredDate = value; }
	public LocalTime getStartTime() { return startTime; }
	public void setStartTime(LocalTime value) { startTime = value; }
	public LocalTime getEndTime() { return endTime; }
	public void setEndTime(LocalTime value) { endTime = value; }
	public LocalDateTime getNotificationAt() { return notificationAt; }
	public void setNotificationAt(LocalDateTime value) { notificationAt = value; }
	public LocalDate getPeriodStart() { return periodStart; }
	public void setPeriodStart(LocalDate value) { periodStart = value; }
	public LocalDate getPeriodEnd() { return periodEnd; }
	public void setPeriodEnd(LocalDate value) { periodEnd = value; }
	public String getPostalCode() { return postalCode; }
	public void setPostalCode(String value) { postalCode = value; }
	public String getPrefecture() { return prefecture; }
	public void setPrefecture(String value) { prefecture = value; }
	public String getAddress() { return address; }
	public void setAddress(String value) { address = value; }
	public String getBuildingName() { return buildingName; }
	public void setBuildingName(String value) { buildingName = value; }
	public Integer getQuantity() { return quantity; }
	public void setQuantity(Integer value) { quantity = value; }
	public BigDecimal getAmount() { return amount; }
	public void setAmount(BigDecimal value) { amount = value; }
	public Integer getPercentage() { return percentage; }
	public void setPercentage(Integer value) { percentage = value; }
	public Integer getRating() { return rating; }
	public void setRating(Integer value) { rating = value; }
	public String getGender() { return gender; }
	public void setGender(String value) { gender = value; }
	public List<String> getLanguages() { return languages; }
	public void setLanguages(List<String> value) { languages = new ArrayList<>(value); }
	public String getContactMethod() { return contactMethod; }
	public void setContactMethod(String value) { contactMethod = value; }
	public Integer getPriority() { return priority; }
	public void setPriority(Integer value) { priority = value; }
	public String getThemeColor() { return themeColor; }
	public void setThemeColor(String value) { themeColor = value; }
	public String getAttachmentFileName() { return attachmentFileName; }
	public void setAttachmentFileName(String value) { attachmentFileName = value; }
	public String getSubject() { return subject; }
	public void setSubject(String value) { subject = value; }
	public String getContent() { return content; }
	public void setContent(String value) { content = value; }
	public String getConfidentiality() { return confidentiality; }
	public void setConfidentiality(String value) { confidentiality = value; }
	public boolean isTermsAgreed() { return termsAgreed; }
	public void setTermsAgreed(boolean value) { termsAgreed = value; }
	public boolean isMarketingOptIn() { return marketingOptIn; }
	public void setMarketingOptIn(boolean value) { marketingOptIn = value; }
	public String getStatus() { return status; }
	public String getCreatedBy() { return createdBy; }
	public void setCreatedBy(String value) { createdBy = value; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
