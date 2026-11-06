package com.medsync.appointment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.medsync.appointment.entity.DoctorScheduleLock;

import jakarta.persistence.LockModeType;

@Repository
public interface DoctorScheduleLockRepository extends JpaRepository<DoctorScheduleLock, Long> {

	/*
	 * Create the lock row if it does not already exist.
	 *
	 * INSERT IGNORE is MySQL-specific and makes concurrent first-time lock-row
	 * creation safe.
	 */
	@Modifying
	@Query(value = """
			INSERT IGNORE INTO doctor_schedule_locks
			(doctor_auth_user_id)
			VALUES (:doctorAuthUserId)
			""", nativeQuery = true)
	int createIfAbsent(@Param("doctorAuthUserId") Long doctorAuthUserId);

	/*
	 * Acquire an exclusive database lock for the doctor.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT l
			FROM DoctorScheduleLock l
			WHERE l.doctorAuthUserId = :doctorAuthUserId
			""")
	Optional<DoctorScheduleLock> findByDoctorAuthUserIdForUpdate(@Param("doctorAuthUserId") Long doctorAuthUserId);
}