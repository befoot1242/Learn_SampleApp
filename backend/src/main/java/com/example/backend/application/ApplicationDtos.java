package com.example.backend.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ApplicationDtos {

	private ApplicationDtos() {
	}

	public record CreateApplicationRequest(
			@NotBlank @Size(max = 30) String applicationType,
			@NotBlank @Size(max = 100) String nameKanji,
			@NotBlank @Size(max = 100) @Pattern(regexp = "[ァ-ヶー 　]+", message = "氏名（カナ）は全角カタカナで入力してください") String nameKana,
			@NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z .'-]+", message = "英字氏名は半角英字で入力してください") String nameAlphabet,
			@NotBlank @Pattern(regexp = "[A-Z0-9]{6,12}", message = "社員番号は6～12文字の半角英大文字・数字で入力してください") String employeeNumber,
			@NotBlank @Email @Size(max = 255) String email,
			@NotBlank @Pattern(regexp = "0\\d{1,4}-?\\d{1,4}-?\\d{3,4}", message = "電話番号の形式が正しくありません") String phoneNumber,
			@Pattern(regexp = "(|https?://[^\\s]+)", message = "WebサイトURLはhttp://またはhttps://で入力してください") @Size(max = 255) String websiteUrl,
			@NotNull @Past LocalDate birthDate,
			@NotNull @FutureOrPresent LocalDate desiredDate,
			@NotNull LocalTime startTime,
			@NotNull LocalTime endTime,
			@FutureOrPresent LocalDateTime notificationAt,
			@NotNull LocalDate periodStart,
			@NotNull LocalDate periodEnd,
			@NotBlank @Pattern(regexp = "\\d{3}-?\\d{4}", message = "郵便番号は123-4567形式で入力してください") String postalCode,
			@NotBlank @Size(max = 10) String prefecture,
			@NotBlank @Size(max = 200) String address,
			@Size(max = 200) String buildingName,
			@NotNull @Min(1) @Max(9999) Integer quantity,
			@NotNull @DecimalMin(value = "0.01", message = "金額は0.01以上で入力してください") BigDecimal amount,
			@NotNull @Min(0) @Max(100) Integer percentage,
			@NotNull @Min(1) @Max(5) Integer rating,
			@NotBlank @Size(max = 20) String gender,
			@NotEmpty @Size(max = 5) List<@Pattern(regexp = "(Java|TypeScript|Python|SQL|Other)") String> languages,
			@NotBlank @Size(max = 20) String contactMethod,
			@NotNull @Min(1) @Max(5) Integer priority,
			@NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "テーマカラーの形式が正しくありません") String themeColor,
			@Size(max = 255) String attachmentFileName,
			@NotBlank @Size(max = 200) String subject,
			@NotBlank @Size(max = 2000) String content,
			@NotBlank @Size(max = 20) String confidentiality,
			@NotNull @AssertTrue(message = "利用規約への同意が必要です") Boolean termsAgreed,
			@NotNull Boolean marketingOptIn) {
	}

	public record ApplicationResponse(Long id, String status, LocalDateTime createdAt) {
		public static ApplicationResponse from(ApplicationRequest request) {
			return new ApplicationResponse(request.getId(), request.getStatus(), request.getCreatedAt());
		}
	}
}
