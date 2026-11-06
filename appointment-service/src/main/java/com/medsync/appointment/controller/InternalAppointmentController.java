package com.medsync.appointment.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medsync.appointment.exception.AppointmentAccessDeniedException;
import com.medsync.appointment.service.AppointmentService;

@RestController
@RequestMapping("/api/internal/assignments")
public class InternalAppointmentController {

	private final AppointmentService appointmentService;

	public InternalAppointmentController(AppointmentService appointmentService) {
		this.appointmentService = appointmentService;
	}

	@GetMapping("/active")
	@PreAuthorize("hasRole('DOCTOR')")
	public ResponseEntity<Map<String, Boolean>> checkActiveAssignment(@RequestParam Long patientId,
			@RequestParam Long doctorAuthUserId, @AuthenticationPrincipal Jwt jwt) {

		Long authenticatedDoctorId = getAuthenticatedUserId(jwt);

		if (!authenticatedDoctorId.equals(doctorAuthUserId)) {
			throw new AppointmentAccessDeniedException("Doctors can only check their own patient assignments.");
		}

		boolean active = appointmentService.hasActiveDoctorPatientAssignment(patientId, doctorAuthUserId);

		return ResponseEntity.ok(Map.of("active", active));
	}

	private Long getAuthenticatedUserId(Jwt jwt) {

		Object userId = jwt.getClaim("userId");

		if (userId == null) {
			throw new AppointmentAccessDeniedException("Authenticated user ID is missing from the JWT.");
		}

		try {
			return Long.valueOf(userId.toString());
		} catch (NumberFormatException exception) {
			throw new AppointmentAccessDeniedException("Authenticated user ID in the JWT is invalid.");
		}
	}
}