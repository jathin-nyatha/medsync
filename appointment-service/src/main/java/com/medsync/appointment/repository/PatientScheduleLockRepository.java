package com.medsync.appointment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.medsync.appointment.entity.PatientScheduleLock;

import jakarta.persistence.LockModeType;

@Repository
public interface PatientScheduleLockRepository extends JpaRepository<PatientScheduleLock, Long> {

	/*
	 * Create the lock row if it does not already exist.
	 */
	@Modifying
	@Query(value = """
			INSERT IGNORE INTO patient_schedule_locks
			(patient_id)
			VALUES (:patientId)
			""", nativeQuery = true)
	int createIfAbsent(@Param("patientId") Long patientId);

	/*
	 * Acquire an exclusive database lock for the patient.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT l
			FROM PatientScheduleLock l
			WHERE l.patientId = :patientId
			""")
	Optional<PatientScheduleLock> findByPatientIdForUpdate(@Param("patientId") Long patientId);
}