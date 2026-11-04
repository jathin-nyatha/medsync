
package com.medsync.doctor.service;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import com.medsync.doctor.client.AuthServiceClient;
import com.medsync.doctor.dto.DoctorEligibilityResponse;
import com.medsync.doctor.entity.Doctor;
import com.medsync.doctor.entity.DoctorStatus;
import com.medsync.doctor.exception.AuthServiceAuthenticationException;
import com.medsync.doctor.exception.AuthServiceAuthorizationException;
import com.medsync.doctor.exception.AuthServiceUnavailableException;
import com.medsync.doctor.exception.DoctorNotFoundException;
import com.medsync.doctor.exception.DuplicateDoctorException;
import com.medsync.doctor.exception.InvalidAuthUserException;
import com.medsync.doctor.exception.InvalidPaginationException;
import com.medsync.doctor.repository.DoctorRepository;

@Service
public class DoctorService {

	private final DoctorRepository doctorRepository;
	private final AuthServiceClient authServiceClient;
	private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "firstName", "lastName", "specialization",
			"status", "createdAt", "updatedAt");

	public DoctorService(DoctorRepository doctorRepository, AuthServiceClient authServiceClient) {

		this.doctorRepository = doctorRepository;
		this.authServiceClient = authServiceClient;
	}

	// Create a doctor profile after validating the Auth Service account.
	public Doctor createDoctor(Long authUserId, String firstName, String lastName, String specialization,
			String licenseNumber, String phoneNumber, String authorizationHeader) {

		validateAuthUserEligibility(authUserId, authorizationHeader);

		if (doctorRepository.existsByAuthUserId(authUserId)) {
			throw new DuplicateDoctorException("A doctor profile already exists for this user.");
		}

		String normalizedLicense = licenseNumber.trim();

		if (doctorRepository.existsByLicenseNumber(normalizedLicense)) {
			throw new DuplicateDoctorException("Medical license number is already registered.");
		}

		Doctor doctor = new Doctor(authUserId, firstName.trim(), lastName.trim(), specialization.trim(),
				normalizedLicense, phoneNumber.trim());

		return doctorRepository.save(doctor);
	}

	private void validatePageable(Pageable pageable) {

		if (pageable == null) {
			throw new InvalidPaginationException("Pagination information is required.");
		}

		for (Sort.Order order : pageable.getSort()) {

			if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
				throw new InvalidPaginationException("Sorting by '" + order.getProperty() + "' is not supported.");
			}
		}
	}

	private void validateAuthUserEligibility(Long authUserId, String authorizationHeader) {

		try {
			DoctorEligibilityResponse response = authServiceClient.checkDoctorEligibility(authUserId,
					authorizationHeader);

			if (response == null || !response.isEligible()) {
				throw new InvalidAuthUserException("Auth user must have an enabled DOCTOR account.");
			}

		} catch (RestClientResponseException exception) {

			int status = exception.getStatusCode().value();

			if (status == 401) {
				throw new AuthServiceAuthenticationException("Auth Service rejected the supplied credentials.");
			}

			if (status == 403) {
				throw new AuthServiceAuthorizationException(
						"Caller is not authorized to access the Auth Service endpoint.");
			}

			if (status == 404) {
				throw new InvalidAuthUserException("No Auth Service user exists with ID: " + authUserId);
			}

			throw new AuthServiceUnavailableException("Auth Service returned an unexpected HTTP error.", exception);

		} catch (ResourceAccessException exception) {

			throw new AuthServiceUnavailableException("Unable to communicate with Auth Service.", exception);
		}
	}

	@Transactional(readOnly = true)
	public Doctor getDoctorById(Long id) {

		return doctorRepository.findById(id)
				.orElseThrow(() -> new DoctorNotFoundException("Doctor not found with ID: " + id));
	}

	@Transactional(readOnly = true)
	public Doctor getDoctorByAuthUserId(Long authUserId) {

		return doctorRepository.findByAuthUserId(authUserId)
				.orElseThrow(() -> new DoctorNotFoundException("Doctor profile not found for user ID: " + authUserId));
	}

	@Transactional(readOnly = true)
	public Page<Doctor> getAllDoctors(Pageable pageable) {

		return doctorRepository.findAll(pageable);
	}

	@Transactional(readOnly = true)
	public Page<Doctor> getDoctorsByStatus(DoctorStatus status, Pageable pageable) {

		return doctorRepository.findByStatus(status, pageable);
	}

	@Transactional(readOnly = true)
	public Page<Doctor> searchDoctors(DoctorStatus status, Pageable pageable) {

		validatePageable(pageable);

		if (status == null) {
			return doctorRepository.findAll(pageable);
		}

		return doctorRepository.findByStatus(status, pageable);
	}

	@Transactional
	public Doctor updateDoctor(Long id, String firstName, String lastName, String specialization, String licenseNumber,
			String phoneNumber) {

		Doctor doctor = getDoctorById(id);

		String normalizedLicense = licenseNumber.trim();

		boolean licenseUsedByAnotherDoctor = doctorRepository.existsByLicenseNumberAndIdNot(normalizedLicense, id);

		if (licenseUsedByAnotherDoctor) {
			throw new DuplicateDoctorException("Medical license number is already registered.");
		}

		doctor.setFirstName(firstName.trim());
		doctor.setLastName(lastName.trim());
		doctor.setSpecialization(specialization.trim());
		doctor.setLicenseNumber(normalizedLicense);
		doctor.setPhoneNumber(phoneNumber.trim());

		return doctorRepository.save(doctor);
	}

	@Transactional
	public Doctor updateDoctorStatus(Long id, DoctorStatus status) {

		Doctor doctor = getDoctorById(id);
		doctor.setStatus(status);

		return doctorRepository.save(doctor);
	}
}
