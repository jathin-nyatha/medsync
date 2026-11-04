
package com.medsync.doctor.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medsync.doctor.entity.Doctor;
import com.medsync.doctor.entity.DoctorStatus;
import com.medsync.doctor.exception.DoctorNotFoundException;
import com.medsync.doctor.exception.DuplicateDoctorException;
import com.medsync.doctor.repository.DoctorRepository;

@Service
@Transactional
public class DoctorService {

	private final DoctorRepository doctorRepository;

	public DoctorService(DoctorRepository doctorRepository) {
		this.doctorRepository = doctorRepository;
	}

	// Create a doctor profile
	public Doctor createDoctor(Long authUserId, String firstName, String lastName, String specialization,
			String licenseNumber, String phoneNumber) {

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

	// Retrieve doctor by primary key
	@Transactional(readOnly = true)
	public Doctor getDoctorById(Long id) {

		return doctorRepository.findById(id)
				.orElseThrow(() -> new DoctorNotFoundException("Doctor not found with ID: " + id));
	}

	// Retrieve doctor by Auth Service user ID
	@Transactional(readOnly = true)
	public Doctor getDoctorByAuthUserId(Long authUserId) {

		return doctorRepository.findByAuthUserId(authUserId)
				.orElseThrow(() -> new DoctorNotFoundException("Doctor profile not found for user ID: " + authUserId));
	}

	// Retrieve all doctors with pagination
	@Transactional(readOnly = true)
	public Page<Doctor> getAllDoctors(Pageable pageable) {

		return doctorRepository.findAll(pageable);
	}

	// Retrieve doctors by status with pagination
	@Transactional(readOnly = true)
	public Page<Doctor> getDoctorsByStatus(DoctorStatus status, Pageable pageable) {

		return doctorRepository.findByStatus(status, pageable);
	}

	// Update professional information
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

	// Activate or deactivate a doctor profile
	public Doctor updateDoctorStatus(Long id, DoctorStatus status) {

		Doctor doctor = getDoctorById(id);

		doctor.setStatus(status);

		return doctorRepository.save(doctor);
	}
}
