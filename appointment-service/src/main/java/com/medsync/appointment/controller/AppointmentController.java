package com.medsync.appointment.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medsync.appointment.dto.AppointmentNotificationContext;
import com.medsync.appointment.dto.AppointmentResponse;
import com.medsync.appointment.dto.CancelAppointmentRequest;
import com.medsync.appointment.dto.CreateAppointmentRequest;
import com.medsync.appointment.dto.RescheduleAppointmentRequest;
import com.medsync.appointment.entity.AppointmentStatus;
import com.medsync.appointment.exception.AppointmentAccessDeniedException;
import com.medsync.appointment.service.AppointmentService;
import com.medsync.appointment.service.AppointmentValidationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/appointments")
@Validated
public class AppointmentController {

	private final AppointmentService appointmentService;
	private final AppointmentValidationService appointmentValidationService;

	public AppointmentController(AppointmentService appointmentService,
			AppointmentValidationService appointmentValidationService) {

		this.appointmentService = appointmentService;
		this.appointmentValidationService = appointmentValidationService;
	}

	@PostMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
	public ResponseEntity<AppointmentResponse> createAppointment(@Valid @RequestBody CreateAppointmentRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		Long createdByAuthUserId = getAuthenticatedUserId(jwt);

		AppointmentNotificationContext context = appointmentValidationService
				.resolveNotificationContext(request.patientId(), request.doctorAuthUserId());

		AppointmentResponse response = appointmentService.createAppointment(request, createdByAuthUserId, context);

		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/{appointmentId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR')")
	public ResponseEntity<AppointmentResponse> getAppointmentById(@PathVariable Long appointmentId,
			@AuthenticationPrincipal Jwt jwt) {

		AppointmentResponse response;

		if (hasRole(jwt, "DOCTOR")) {
			response = appointmentService.getDoctorAppointment(appointmentId, getAuthenticatedUserId(jwt));
		} else {
			response = appointmentService.getAppointmentById(appointmentId);
		}

		return ResponseEntity.ok(response);
	}

	@GetMapping("/patient/{patientId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
	public ResponseEntity<Page<AppointmentResponse>> getPatientAppointments(@PathVariable Long patientId,
			@PageableDefault(size = 10, sort = "startAt") Pageable pageable) {

		return ResponseEntity.ok(appointmentService.getPatientAppointments(patientId, pageable));
	}

	@GetMapping("/doctor/{doctorAuthUserId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR')")
	public ResponseEntity<Page<AppointmentResponse>> getDoctorAppointments(@PathVariable Long doctorAuthUserId,
			@AuthenticationPrincipal Jwt jwt, @PageableDefault(size = 10, sort = "startAt") Pageable pageable) {

		Long requestedDoctorId = doctorAuthUserId;

		if (hasRole(jwt, "DOCTOR")) {

			Long authenticatedDoctorId = getAuthenticatedUserId(jwt);

			if (!authenticatedDoctorId.equals(doctorAuthUserId)) {
				throw new AppointmentAccessDeniedException("Doctors can only view their own appointments.");
			}

			requestedDoctorId = authenticatedDoctorId;
		}

		return ResponseEntity.ok(appointmentService.getDoctorAppointments(requestedDoctorId, pageable));
	}

	@PatchMapping("/{appointmentId}/reschedule")
	@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
	public ResponseEntity<AppointmentResponse> rescheduleAppointment(@PathVariable Long appointmentId,
			@Valid @RequestBody RescheduleAppointmentRequest request) {

		AppointmentResponse existingAppointment = appointmentService.getAppointmentById(appointmentId);

		AppointmentNotificationContext context = resolveContext(existingAppointment);

		AppointmentResponse response = appointmentService.rescheduleAppointment(appointmentId, request, context);

		return ResponseEntity.ok(response);
	}

	@PatchMapping("/{appointmentId}/cancel")
	@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR')")
	public ResponseEntity<AppointmentResponse> cancelAppointment(@PathVariable Long appointmentId,
			@Valid @RequestBody CancelAppointmentRequest request, @AuthenticationPrincipal Jwt jwt) {

		AppointmentResponse existingAppointment;

		if (hasRole(jwt, "DOCTOR")) {
			existingAppointment = appointmentService.getDoctorAppointment(appointmentId, getAuthenticatedUserId(jwt));
		} else {
			existingAppointment = appointmentService.getAppointmentById(appointmentId);
		}

		AppointmentNotificationContext context = resolveContext(existingAppointment);

		AppointmentResponse response = appointmentService.cancelAppointment(appointmentId, request, context);

		return ResponseEntity.ok(response);
	}

	@PatchMapping("/{appointmentId}/status")
	@PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
	public ResponseEntity<AppointmentResponse> updateAppointmentStatus(@PathVariable Long appointmentId,
			@RequestParam AppointmentStatus status, @AuthenticationPrincipal Jwt jwt) {

		AppointmentResponse existingAppointment;

		if (hasRole(jwt, "DOCTOR")) {
			existingAppointment = appointmentService.getDoctorAppointment(appointmentId, getAuthenticatedUserId(jwt));
		} else {
			existingAppointment = appointmentService.getAppointmentById(appointmentId);
		}

		AppointmentNotificationContext context = null;

		if (status == AppointmentStatus.CANCELLED) {
			context = resolveContext(existingAppointment);
		}

		AppointmentResponse response = appointmentService.updateAppointmentStatus(appointmentId, status, context);

		return ResponseEntity.ok(response);
	}

	private AppointmentNotificationContext resolveContext(AppointmentResponse appointment) {

		return appointmentValidationService.resolveNotificationContext(appointment.patientId(),
				appointment.doctorAuthUserId());
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

	private boolean hasRole(Jwt jwt, String expectedRole) {

		String role = jwt.getClaimAsString("role");

		return expectedRole.equals(role);
	}
}