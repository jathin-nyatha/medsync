package com.medsync.appointment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medsync.appointment.entity.DoctorScheduleLock;
import com.medsync.appointment.entity.PatientScheduleLock;
import com.medsync.appointment.repository.DoctorScheduleLockRepository;
import com.medsync.appointment.repository.PatientScheduleLockRepository;

@Service
public class ScheduleLockService {

	private final DoctorScheduleLockRepository doctorLockRepository;
	private final PatientScheduleLockRepository patientLockRepository;

	public ScheduleLockService(DoctorScheduleLockRepository doctorLockRepository,
			PatientScheduleLockRepository patientLockRepository) {

		this.doctorLockRepository = doctorLockRepository;
		this.patientLockRepository = patientLockRepository;
	}

	/**
	 * Must be invoked from an existing transaction.
	 *
	 * Lock order is always: 1. Doctor 2. Patient
	 *
	 * Keeping this order consistent reduces deadlock risk.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void acquireLocks(Long doctorAuthUserId, Long patientId) {

		doctorLockRepository.createIfAbsent(doctorAuthUserId);

		DoctorScheduleLock doctorLock = doctorLockRepository.findByDoctorAuthUserIdForUpdate(doctorAuthUserId)
				.orElseThrow(() -> new IllegalStateException("Unable to acquire doctor schedule lock."));

		patientLockRepository.createIfAbsent(patientId);

		PatientScheduleLock patientLock = patientLockRepository.findByPatientIdForUpdate(patientId)
				.orElseThrow(() -> new IllegalStateException("Unable to acquire patient schedule lock."));

		// Keep references alive until the transaction completes.
		if (doctorLock.getDoctorAuthUserId() == null || patientLock.getPatientId() == null) {
			throw new IllegalStateException("Invalid schedule lock.");
		}
	}
}