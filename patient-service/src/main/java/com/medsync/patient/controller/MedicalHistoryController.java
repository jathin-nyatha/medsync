
package com.medsync.patient.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medsync.patient.dto.CreateMedicalHistoryRequest;
import com.medsync.patient.dto.MedicalHistoryResponse;
import com.medsync.patient.service.MedicalHistoryService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/patients/{patientId}/medical-history")
@PreAuthorize("hasRole('DOCTOR')")
@Validated
public class MedicalHistoryController {

	private final MedicalHistoryService medicalHistoryService;

	public MedicalHistoryController(MedicalHistoryService medicalHistoryService) {
		this.medicalHistoryService = medicalHistoryService;
	}

	// Create medical history for a patient
	@PostMapping
	public ResponseEntity<MedicalHistoryResponse> createMedicalHistory(@PathVariable @Positive Long patientId,
			@AuthenticationPrincipal Jwt jwt, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
			@Valid @RequestBody CreateMedicalHistoryRequest request) {

		Long doctorAuthUserId = getAuthenticatedUserId(jwt);

		MedicalHistoryResponse response = medicalHistoryService.createMedicalHistory(patientId, doctorAuthUserId,
				authorizationHeader, request);

		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	// Retrieve paginated medical history for a patient
	@GetMapping
	public ResponseEntity<Page<MedicalHistoryResponse>> getMedicalHistory(@PathVariable @Positive Long patientId,
			@AuthenticationPrincipal Jwt jwt, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
			@PageableDefault(page = 0, size = 20, sort = "recordedAt", direction = Sort.Direction.DESC) Pageable pageable) {

		Long doctorAuthUserId = getAuthenticatedUserId(jwt);

		Page<MedicalHistoryResponse> response = medicalHistoryService.getMedicalHistory(patientId, doctorAuthUserId,
				authorizationHeader, pageable);

		return ResponseEntity.ok(response);
	}

	// Extract the authenticated doctor's ID from the validated JWT
	private Long getAuthenticatedUserId(Jwt jwt) {

		Number userId = jwt.getClaim("userId");

		if (userId == null || userId.longValue() <= 0) {
			throw new IllegalStateException("Authenticated token does not contain a valid userId");
		}

		return userId.longValue();
	}
}
