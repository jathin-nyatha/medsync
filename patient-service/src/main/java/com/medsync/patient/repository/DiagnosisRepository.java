
package com.medsync.patient.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.medsync.patient.entity.Diagnosis;

@Repository
public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {

	// Retrieve all diagnoses belonging to a specific patient
	Page<Diagnosis> findByPatient_Id(Long patientId, Pageable pageable);

	// Check whether a patient has any diagnoses
	boolean existsByPatient_Id(Long patientId);

	// Retrieve a diagnosis only if it belongs to the specified patient
	Optional<Diagnosis> findByIdAndPatient_Id(Long diagnosisId, Long patientId);

	// Retrieve diagnoses recorded by a specific doctor
	Page<Diagnosis> findByDiagnosedByAuthUserId(Long diagnosedByAuthUserId, Pageable pageable);
}
