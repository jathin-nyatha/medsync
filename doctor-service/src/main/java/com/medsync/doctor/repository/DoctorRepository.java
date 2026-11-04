
package com.medsync.doctor.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.medsync.doctor.entity.Doctor;
import com.medsync.doctor.entity.DoctorStatus;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

	// Find a doctor using their Auth Service user ID
	Optional<Doctor> findByAuthUserId(Long authUserId);

	// Check whether an Auth Service user already has a doctor profile
	boolean existsByAuthUserId(Long authUserId);

	// Check whether a medical license number already exists
	boolean existsByLicenseNumber(String licenseNumber);

	boolean existsByLicenseNumberAndIdNot(String licenseNumber, Long id);

	// Retrieve doctors by status with pagination
	Page<Doctor> findByStatus(DoctorStatus status, Pageable pageable);
}
