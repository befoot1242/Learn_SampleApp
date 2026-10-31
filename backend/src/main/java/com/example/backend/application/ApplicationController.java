package com.example.backend.application;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

	private final ApplicationService applicationService;

	public ApplicationController(ApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	@PostMapping
	public ApplicationDtos.ApplicationResponse create(
			@Valid @RequestBody ApplicationDtos.CreateApplicationRequest request,
			Authentication authentication) {
		return ApplicationDtos.ApplicationResponse.from(applicationService.create(request, authentication.getName()));
	}
}
