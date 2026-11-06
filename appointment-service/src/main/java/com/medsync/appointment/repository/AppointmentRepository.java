package com.medsync.appointment.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.medsync.appointment.entity.Appointment;
import com.medsync.appointment.entity.AppointmentStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

	Page<Appointment> findByPatientId(Long patientId, Pageable pageable);

	Page<Appointment> findByDoctorAuthUserId(Long doctorAuthUserId, Pageable pageable);

	Page<Appointment> findByPatientIdAndStatusIn(Long patientId, Collection<AppointmentStatus> statuses,
			Pageable pageable);

	Page<Appointment> findByDoctorAuthUserIdAndStatusIn(Long doctorAuthUserId, Collection<AppointmentStatus> statuses,
			Pageable pageable);

	Optional<Appointment> findByIdAndPatientId(Long appointmentId, Long patientId);

	Optional<Appointment> findByIdAndDoctorAuthUserId(Long appointmentId, Long doctorAuthUserId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT a
			FROM Appointment a
			WHERE a.id = :appointmentId
			""")
	Optional<Appointment> findByIdForUpdate(@Param("appointmentId") Long appointmentId);

	@Query("""
			SELECT CASE WHEN COUNT(a) > 0 THEN TRUE ELSE FALSE END
			FROM Appointment a
			WHERE a.doctorAuthUserId = :doctorAuthUserId
			  AND a.status IN :statuses
			  AND a.startAt < :requestedEnd
			  AND a.endAt > :requestedStart
			""")
	boolean existsDoctorScheduleConflict(@Param("doctorAuthUserId") Long doctorAuthUserId,
			@Param("statuses") Collection<AppointmentStatus> statuses, @Param("requestedStart") Instant requestedStart,
			@Param("requestedEnd") Instant requestedEnd);

	@Query("""
			SELECT CASE WHEN COUNT(a) > 0 THEN TRUE ELSE FALSE END
			FROM Appointment a
			WHERE a.patientId = :patientId
			  AND a.status IN :statuses
			  AND a.startAt < :requestedEnd
			  AND a.endAt > :requestedStart
			""")
	boolean existsPatientScheduleConflict(@Param("patientId") Long patientId,
			@Param("statuses") Collection<AppointmentStatus> statuses, @Param("requestedStart") Instant requestedStart,
			@Param("requestedEnd") Instant requestedEnd);

	@Query("""
			SELECT CASE WHEN COUNT(a) > 0 THEN TRUE ELSE FALSE END
			FROM Appointment a
			WHERE a.doctorAuthUserId = :doctorAuthUserId
			  AND a.status IN :statuses
			  AND a.startAt < :requestedEnd
			  AND a.endAt > :requestedStart
			  AND a.id <> :excludedAppointmentId
			""")
	boolean existsDoctorScheduleConflictExcluding(@Param("doctorAuthUserId") Long doctorAuthUserId,
			@Param("statuses") Collection<AppointmentStatus> statuses, @Param("requestedStart") Instant requestedStart,
			@Param("requestedEnd") Instant requestedEnd, @Param("excludedAppointmentId") Long excludedAppointmentId);

	@Query("""
			SELECT CASE WHEN COUNT(a) > 0 THEN TRUE ELSE FALSE END
			FROM Appointment a
			WHERE a.patientId = :patientId
			  AND a.status IN :statuses
			  AND a.startAt < :requestedEnd
			  AND a.endAt > :requestedStart
			  AND a.id <> :excludedAppointmentId
			""")
	boolean existsPatientScheduleConflictExcluding(@Param("patientId") Long patientId,
			@Param("statuses") Collection<AppointmentStatus> statuses, @Param("requestedStart") Instant requestedStart,
			@Param("requestedEnd") Instant requestedEnd, @Param("excludedAppointmentId") Long excludedAppointmentId);

	@Query("""
			SELECT CASE WHEN COUNT(a) > 0 THEN TRUE ELSE FALSE END
			FROM Appointment a
			WHERE a.patientId = :patientId
			  AND a.doctorAuthUserId = :doctorAuthUserId
			  AND a.status IN :activeStatuses
			""")
	boolean existsActiveDoctorPatientAssignment(@Param("patientId") Long patientId,
			@Param("doctorAuthUserId") Long doctorAuthUserId,
			@Param("activeStatuses") Collection<AppointmentStatus> activeStatuses);
}