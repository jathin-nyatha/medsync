package com.medsync.patient.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medsync.patient.dto.CreatePatientRequest;
import com.medsync.patient.dto.PatientResponse;
import com.medsync.patient.dto.UpdatePatientRequest;
import com.medsync.patient.service.PatientService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

	private final PatientService patientService;

	public PatientController(PatientService patientService) {
		this.patientService = patientService;
	}

	// Register a patient. ADMIN and RECEPTIONIST only.
	@PostMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
	public ResponseEntity<PatientResponse> createPatient(@Valid @RequestBody CreatePatientRequest request) {

		PatientResponse response = patientService.createPatient(request.getAuthUserId(), request);

		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	// Retrieve a patient by Patient Service ID.
	@GetMapping("/{patientId}")
	@PreAuthorize("""
			hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR')
			or @serviceTokenAuthorization.isAppointmentServiceWithScope(authentication.token)
			""")
	public ResponseEntity<PatientResponse> getPatientById(@PathVariable Long patientId) {

		return ResponseEntity.ok(patientService.getPatientById(patientId));
	}

	// Retrieve all patients with pagination.
	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR')")
	public ResponseEntity<Page<PatientResponse>> getPatients(
			@PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

		return ResponseEntity.ok(patientService.getPatients(pageable));
	}

	// Update a patient profile. ADMIN and RECEPTIONIST only.
	@PutMapping("/{patientId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
	public ResponseEntity<PatientResponse> updatePatient(@PathVariable Long patientId,
			@Valid @RequestBody UpdatePatientRequest request) {

		return ResponseEntity.ok(patientService.updatePatientById(patientId, request));
	}
}