package com.medsync.doctor.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medsync.doctor.dto.CreateDoctorRequest;
import com.medsync.doctor.dto.DoctorResponse;
import com.medsync.doctor.dto.UpdateDoctorRequest;
import com.medsync.doctor.dto.UpdateDoctorStatusRequest;
import com.medsync.doctor.entity.Doctor;
import com.medsync.doctor.entity.DoctorStatus;
import com.medsync.doctor.service.DoctorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

	private final DoctorService doctorService;

	public DoctorController(DoctorService doctorService) {
		this.doctorService = doctorService;
	}

	// 1. Create a doctor profile. ADMIN only.
	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<DoctorResponse> createDoctor(@Valid @RequestBody CreateDoctorRequest request,
			@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {

		Doctor doctor = doctorService.createDoctor(request.getAuthUserId(), request.getFirstName(),
				request.getLastName(), request.getSpecialization(), request.getLicenseNumber(),
				request.getPhoneNumber(), authorizationHeader);

		return ResponseEntity.status(HttpStatus.CREATED).body(DoctorResponse.fromEntity(doctor));
	}

	// 2. Get doctor by Doctor ID.
	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
	public ResponseEntity<DoctorResponse> getDoctorById(@PathVariable Long id) {

		Doctor doctor = doctorService.getDoctorById(id);

		return ResponseEntity.ok(DoctorResponse.fromEntity(doctor));
	}

	// 3. Get doctor by Auth Service user ID.
	@GetMapping("/by-auth-user/{authUserId}")
	@PreAuthorize("""
			hasAnyRole('ADMIN', 'RECEPTIONIST')
			or @serviceTokenAuthorization.isAppointmentServiceWithScope(authentication.token)
			""")
	public ResponseEntity<DoctorResponse> getDoctorByAuthUserId(@PathVariable Long authUserId) {

		Doctor doctor = doctorService.getDoctorByAuthUserId(authUserId);

		return ResponseEntity.ok(DoctorResponse.fromEntity(doctor));
	}

	// 4. Get all doctors, optionally filtered by status.
	// Supports pagination and sorting.
	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
	public ResponseEntity<Page<DoctorResponse>> getAllDoctors(@RequestParam(required = false) DoctorStatus status,
			@PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

		Page<DoctorResponse> doctors = doctorService.searchDoctors(status, pageable).map(DoctorResponse::fromEntity);

		return ResponseEntity.ok(doctors);
	}

	// 5. Update doctor details. ADMIN only.
	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<DoctorResponse> updateDoctor(@PathVariable Long id,
			@Valid @RequestBody UpdateDoctorRequest request) {

		Doctor doctor = doctorService.updateDoctor(id, request.getFirstName(), request.getLastName(),
				request.getSpecialization(), request.getLicenseNumber(), request.getPhoneNumber());

		return ResponseEntity.ok(DoctorResponse.fromEntity(doctor));
	}

	// 6. Activate or deactivate a doctor. ADMIN only.
	@PatchMapping("/{id}/status")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<DoctorResponse> updateDoctorStatus(@PathVariable Long id,
			@Valid @RequestBody UpdateDoctorStatusRequest request) {

		Doctor doctor = doctorService.updateDoctorStatus(id, request.getStatus());

		return ResponseEntity.ok(DoctorResponse.fromEntity(doctor));
	}
}