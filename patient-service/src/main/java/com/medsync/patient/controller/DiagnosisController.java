
package com.medsync.patient.controller;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medsync.patient.dto.CreateDiagnosisRequest;
import com.medsync.patient.dto.DiagnosisResponse;
import com.medsync.patient.dto.UpdateDiagnosisRequest;
import com.medsync.patient.service.DiagnosisService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/patients/{patientId}/diagnoses")
@Validated
@PreAuthorize("hasRole('DOCTOR')")
public class DiagnosisController {

	private final DiagnosisService diagnosisService;

	public DiagnosisController(DiagnosisService diagnosisService) {
		this.diagnosisService = diagnosisService;
	}

	/**
	 * Creates a diagnosis for a patient.
	 *
	 * POST /api/patients/{patientId}/diagnoses
	 */
	@PostMapping
	public ResponseEntity<DiagnosisResponse> createDiagnosis(@PathVariable @Positive Long patientId,
			@Valid @RequestBody CreateDiagnosisRequest request, @AuthenticationPrincipal Jwt jwt,
			@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {

		Long doctorAuthUserId = getAuthenticatedUserId(jwt);

		DiagnosisResponse response = diagnosisService.createDiagnosis(patientId, doctorAuthUserId, authorizationHeader,
				request);

		URI location = URI.create("/api/patients/" + patientId + "/diagnoses/" + response.getId());

		return ResponseEntity.created(location).body(response);
	}

	/**
	 * Retrieves a specific diagnosis belonging to a patient.
	 *
	 * GET /api/patients/{patientId}/diagnoses/{diagnosisId}
	 */
	@GetMapping("/{diagnosisId}")
	public ResponseEntity<DiagnosisResponse> getDiagnosis(@PathVariable @Positive Long patientId,
			@PathVariable @Positive Long diagnosisId, @AuthenticationPrincipal Jwt jwt,
			@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {

		Long doctorAuthUserId = getAuthenticatedUserId(jwt);

		DiagnosisResponse response = diagnosisService.getDiagnosis(patientId, doctorAuthUserId, authorizationHeader,
				diagnosisId);

		return ResponseEntity.ok(response);
	}

	/**
	 * Retrieves paginated diagnoses belonging to a patient.
	 *
	 * GET /api/patients/{patientId}/diagnoses
	 */
	@GetMapping
	public ResponseEntity<Page<DiagnosisResponse>> getDiagnoses(@PathVariable @Positive Long patientId,
			@AuthenticationPrincipal Jwt jwt, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
			@PageableDefault(size = 20, sort = "diagnosedAt", direction = Sort.Direction.DESC) Pageable pageable) {

		Long doctorAuthUserId = getAuthenticatedUserId(jwt);

		Page<DiagnosisResponse> response = diagnosisService.getDiagnoses(patientId, doctorAuthUserId,
				authorizationHeader, pageable);

		return ResponseEntity.ok(response);
	}

	/**
	 * Updates an existing diagnosis belonging to a patient.
	 *
	 * PUT /api/patients/{patientId}/diagnoses/{diagnosisId}
	 */
	@PutMapping("/{diagnosisId}")
	public ResponseEntity<DiagnosisResponse> updateDiagnosis(@PathVariable @Positive Long patientId,
			@PathVariable @Positive Long diagnosisId, @Valid @RequestBody UpdateDiagnosisRequest request,
			@AuthenticationPrincipal Jwt jwt, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {

		Long doctorAuthUserId = getAuthenticatedUserId(jwt);

		DiagnosisResponse response = diagnosisService.updateDiagnosis(patientId, doctorAuthUserId, authorizationHeader,
				diagnosisId, request);

		return ResponseEntity.ok(response);
	}

	/**
	 * Extracts the authenticated user's ID from the validated JWT. The ID is never
	 * accepted from the client request.
	 */
	private Long getAuthenticatedUserId(Jwt jwt) {

		if (jwt == null) {
			throw new IllegalStateException("Authenticated JWT is missing.");
		}

		Object userIdClaim = jwt.getClaim("userId");

		if (!(userIdClaim instanceof Number userId)) {
			throw new IllegalStateException("JWT does not contain a valid userId claim.");
		}

		long authenticatedUserId = userId.longValue();

		if (authenticatedUserId <= 0) {
			throw new IllegalStateException("JWT contains an invalid userId.");
		}

		return authenticatedUserId;
	}
}
