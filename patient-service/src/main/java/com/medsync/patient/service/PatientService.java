
package com.medsync.patient.service;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medsync.patient.dto.CreatePatientRequest;
import com.medsync.patient.dto.PatientResponse;
import com.medsync.patient.dto.UpdatePatientRequest;
import com.medsync.patient.entity.Patient;
import com.medsync.patient.exception.DuplicatePatientProfileException;
import com.medsync.patient.exception.InvalidPaginationException;
import com.medsync.patient.exception.PatientNotFoundException;
import com.medsync.patient.repository.PatientRepository;

@Service
public class PatientService {

	private static final int MAX_PAGE_SIZE = 100;

	private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "firstName", "lastName", "dateOfBirth",
			"createdAt", "updatedAt");

	private final PatientRepository patientRepository;

	public PatientService(PatientRepository patientRepository) {
		this.patientRepository = patientRepository;
	}

	// Create a patient profile for an authenticated user.
	@Transactional
	public PatientResponse createPatient(Long authUserId, CreatePatientRequest request) {

		if (patientRepository.existsByAuthUserId(authUserId)) {
			throw new DuplicatePatientProfileException("A patient profile already exists for this account.");
		}

		Patient patient = new Patient(authUserId, request.getFirstName().trim(), request.getLastName().trim(),
				request.getDateOfBirth(), request.getGender(), request.getPhoneNumber().trim(),
				request.getAddress() == null ? null : request.getAddress().trim(), request.getBloodGroup());

		Patient savedPatient = patientRepository.save(patient);

		return PatientResponse.from(savedPatient);
	}

	// Retrieve the authenticated patient's own profile.
	@Transactional(readOnly = true)
	public PatientResponse getMyProfile(Long authUserId) {

		Patient patient = patientRepository.findByAuthUserId(authUserId)
				.orElseThrow(() -> new PatientNotFoundException("Patient profile not found."));

		return PatientResponse.from(patient);
	}

	// Update the authenticated patient's own profile.
	@Transactional
	public PatientResponse updateMyProfile(Long authUserId, UpdatePatientRequest request) {

		Patient patient = patientRepository.findByAuthUserId(authUserId)
				.orElseThrow(() -> new PatientNotFoundException("Patient profile not found."));

		patient.setFirstName(request.getFirstName().trim());
		patient.setLastName(request.getLastName().trim());
		patient.setDateOfBirth(request.getDateOfBirth());
		patient.setGender(request.getGender());
		patient.setPhoneNumber(request.getPhoneNumber().trim());

		patient.setAddress(request.getAddress() == null ? null : request.getAddress().trim());

		patient.setBloodGroup(request.getBloodGroup());

		Patient updatedPatient = patientRepository.save(patient);

		return PatientResponse.from(updatedPatient);
	}

	// Retrieve a patient using the Patient Service ID.
	@Transactional(readOnly = true)
	public PatientResponse getPatientById(Long patientId) {

		Patient patient = patientRepository.findById(patientId)
				.orElseThrow(() -> new PatientNotFoundException("Patient not found with ID: " + patientId));

		return PatientResponse.from(patient);
	}

	// Retrieve patients using pagination and sorting.
	@Transactional(readOnly = true)
	public Page<PatientResponse> getPatients(Pageable pageable) {

		validatePageable(pageable);

		return patientRepository.findAll(pageable).map(PatientResponse::from);
	}

	@Transactional
	public PatientResponse updatePatientById(Long patientId, UpdatePatientRequest request) {

		Patient patient = patientRepository.findById(patientId)
				.orElseThrow(() -> new PatientNotFoundException("Patient not found with ID: " + patientId));

		patient.setFirstName(request.getFirstName().trim());
		patient.setLastName(request.getLastName().trim());
		patient.setDateOfBirth(request.getDateOfBirth());
		patient.setGender(request.getGender());
		patient.setPhoneNumber(request.getPhoneNumber().trim());

		patient.setAddress(request.getAddress() == null ? null : request.getAddress().trim());

		patient.setBloodGroup(request.getBloodGroup());

		return PatientResponse.from(patientRepository.save(patient));
	}

	// Validate page size and supported sorting properties.
	private void validatePageable(Pageable pageable) {

		if (pageable == null) {
			throw new InvalidPaginationException("Pagination information is required.");
		}

		if (pageable.getPageSize() > MAX_PAGE_SIZE) {
			throw new InvalidPaginationException("Page size cannot exceed " + MAX_PAGE_SIZE + ".");
		}

		pageable.getSort().forEach(order -> {

			if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
				throw new InvalidPaginationException("Unsupported sort field: " + order.getProperty());
			}
		});
	}
}
