package com.medsync.auth.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medsync.auth.dto.DoctorEligibilityResponse;
import com.medsync.auth.dto.UserEmailResponse;
import com.medsync.auth.service.UserService;

@RestController
@RequestMapping("/api/internal/users")
public class InternalUserController {

	private final UserService userService;

	public InternalUserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/{userId}/doctor-eligibility")
	public ResponseEntity<?> checkDoctorEligibility(@PathVariable Long userId) {

		try {
			DoctorEligibilityResponse response = userService.checkDoctorEligibility(userId);

			return ResponseEntity.ok(response);

		} catch (IllegalArgumentException exception) {

			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping("/{userId}/email")
	public ResponseEntity<?> getUserEmail(@PathVariable Long userId) {

		try {
			UserEmailResponse response = userService.getUserEmail(userId);

			return ResponseEntity.ok(response);

		} catch (IllegalArgumentException exception) {

			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", exception.getMessage()));
		}
	}
}