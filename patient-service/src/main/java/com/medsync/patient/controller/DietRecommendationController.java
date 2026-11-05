package com.medsync.patient.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medsync.patient.dto.DietRecommendationRequest;
import com.medsync.patient.dto.DietRecommendationResponse;
import com.medsync.patient.service.DietRecommendationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/patients/{patientId}/diet-recommendations")
public class DietRecommendationController {

	private final DietRecommendationService dietRecommendationService;

	public DietRecommendationController(DietRecommendationService dietRecommendationService) {
		this.dietRecommendationService = dietRecommendationService;
	}

	@PostMapping
	@PreAuthorize("hasRole('DOCTOR')")
	public ResponseEntity<DietRecommendationResponse> generateDietRecommendation(@PathVariable Long patientId,
			@Valid @RequestBody DietRecommendationRequest request, @AuthenticationPrincipal Jwt jwt,
			@RequestHeader("Authorization") String authorizationHeader) {

		Long doctorAuthUserId = extractUserId(jwt);

		DietRecommendationResponse response = dietRecommendationService.generateDietRecommendation(patientId,
				doctorAuthUserId, authorizationHeader, request);

		return ResponseEntity.ok(response);
	}

	private Long extractUserId(Jwt jwt) {
		Object userIdClaim = jwt.getClaim("userId");

		if (userIdClaim == null) {
			throw new IllegalStateException("Authenticated JWT does not contain a userId claim.");
		}

		try {
			return Long.valueOf(userIdClaim.toString());
		} catch (NumberFormatException exception) {
			throw new IllegalStateException("JWT userId claim must contain a numeric value.", exception);
		}
	}
}