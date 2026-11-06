package com.medsync.appointment.service;

import java.time.Instant;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medsync.appointment.dto.AppointmentNotificationContext;
import com.medsync.appointment.dto.AppointmentNotificationPayload;
import com.medsync.appointment.dto.AppointmentResponse;
import com.medsync.appointment.dto.CancelAppointmentRequest;
import com.medsync.appointment.dto.CreateAppointmentRequest;
import com.medsync.appointment.dto.RescheduleAppointmentRequest;
import com.medsync.appointment.entity.Appointment;
import com.medsync.appointment.entity.AppointmentStatus;
import com.medsync.appointment.entity.OutboxEventType;
import com.medsync.appointment.exception.AppointmentConflictException;
import com.medsync.appointment.exception.AppointmentNotFoundException;
import com.medsync.appointment.exception.InvalidAppointmentOperationException;
import com.medsync.appointment.exception.InvalidAppointmentRequestException;
import com.medsync.appointment.exception.InvalidAppointmentTimeException;
import com.medsync.appointment.repository.AppointmentRepository;

@Service
public class AppointmentService {

	private final AppointmentRepository appointmentRepository;
	private final AppointmentMapper appointmentMapper;
	private final ScheduleLockService scheduleLockService;
	private final OutboxEventPublisher outboxEventPublisher;

	private static final Set<AppointmentStatus> BLOCKING_STATUSES = AppointmentStatus.getScheduleBlockingStatuses();

	private static final Set<AppointmentStatus> ACTIVE_ASSIGNMENT_STATUSES = AppointmentStatus
			.getActiveAssignmentStatuses();

	public AppointmentService(AppointmentRepository appointmentRepository, AppointmentMapper appointmentMapper,
			ScheduleLockService scheduleLockService, OutboxEventPublisher outboxEventPublisher) {

		this.appointmentRepository = appointmentRepository;
		this.appointmentMapper = appointmentMapper;
		this.scheduleLockService = scheduleLockService;
		this.outboxEventPublisher = outboxEventPublisher;
	}

	// ---------------------------------------------------------
	// CREATE APPOINTMENT
	// ---------------------------------------------------------

	@Transactional
	public AppointmentResponse createAppointment(CreateAppointmentRequest request, Long createdByAuthUserId,
			AppointmentNotificationContext notificationContext) {

		if (request == null) {
			throw new InvalidAppointmentRequestException("Appointment request is required.");
		}

		validatePositiveId(request.patientId(), "Patient ID");
		validatePositiveId(request.doctorAuthUserId(), "Doctor user ID");
		validatePositiveId(createdByAuthUserId, "Creator user ID");

		validateNotificationContext(notificationContext, request.patientId(), request.doctorAuthUserId());

		validateAppointmentTime(request.startAt(), request.endAt());

		scheduleLockService.acquireLocks(request.doctorAuthUserId(), request.patientId());

		validateNoScheduleConflict(request.doctorAuthUserId(), request.patientId(), request.startAt(), request.endAt(),
				null);

		Appointment appointment = new Appointment();

		appointment.setPatientId(request.patientId());
		appointment.setDoctorAuthUserId(request.doctorAuthUserId());
		appointment.setCreatedByAuthUserId(createdByAuthUserId);
		appointment.setStartAt(request.startAt());
		appointment.setEndAt(request.endAt());
		appointment.setReason(request.reason().trim());
		appointment.setStatus(AppointmentStatus.SCHEDULED);

		Appointment savedAppointment = appointmentRepository.saveAndFlush(appointment);

		publishNotification(OutboxEventType.APPOINTMENT_CONFIRMATION, savedAppointment, notificationContext);

		return appointmentMapper.toResponse(savedAppointment);
	}

	// ---------------------------------------------------------
	// RETRIEVE APPOINTMENT
	// ---------------------------------------------------------

	@Transactional(readOnly = true)
	public AppointmentResponse getAppointmentById(Long appointmentId) {

		return appointmentMapper.toResponse(findAppointment(appointmentId));
	}

	@Transactional(readOnly = true)
	public AppointmentResponse getPatientAppointment(Long appointmentId, Long patientId) {

		validatePositiveId(patientId, "Patient ID");

		Appointment appointment = appointmentRepository.findByIdAndPatientId(appointmentId, patientId)
				.orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

		return appointmentMapper.toResponse(appointment);
	}

	@Transactional(readOnly = true)
	public AppointmentResponse getDoctorAppointment(Long appointmentId, Long doctorAuthUserId) {

		validatePositiveId(doctorAuthUserId, "Doctor user ID");

		Appointment appointment = appointmentRepository.findByIdAndDoctorAuthUserId(appointmentId, doctorAuthUserId)
				.orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

		return appointmentMapper.toResponse(appointment);
	}

	@Transactional(readOnly = true)
	public Page<AppointmentResponse> getPatientAppointments(Long patientId, Pageable pageable) {

		validatePositiveId(patientId, "Patient ID");

		return appointmentRepository.findByPatientId(patientId, pageable).map(appointmentMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public Page<AppointmentResponse> getDoctorAppointments(Long doctorAuthUserId, Pageable pageable) {

		validatePositiveId(doctorAuthUserId, "Doctor user ID");

		return appointmentRepository.findByDoctorAuthUserId(doctorAuthUserId, pageable)
				.map(appointmentMapper::toResponse);
	}

	// ---------------------------------------------------------
	// RESCHEDULE APPOINTMENT
	// ---------------------------------------------------------

	@Transactional
	public AppointmentResponse rescheduleAppointment(Long appointmentId, RescheduleAppointmentRequest request,
			AppointmentNotificationContext notificationContext) {

		validatePositiveId(appointmentId, "Appointment ID");

		if (request == null) {
			throw new InvalidAppointmentRequestException("Reschedule request is required.");
		}

		Appointment initialAppointment = findAppointment(appointmentId);

		validateNotificationContext(notificationContext, initialAppointment.getPatientId(),
				initialAppointment.getDoctorAuthUserId());

		// Consistent lock order: doctor first, patient second.
		scheduleLockService.acquireLocks(initialAppointment.getDoctorAuthUserId(), initialAppointment.getPatientId());

		// Lock the appointment only after acquiring schedule locks.
		Appointment appointment = findAppointmentForUpdate(appointmentId);

		validateUnchangedParticipants(initialAppointment, appointment, "rescheduling");

		validateNotificationContext(notificationContext, appointment.getPatientId(), appointment.getDoctorAuthUserId());

		validateReschedulableStatus(appointment);

		validateAppointmentTime(request.startAt(), request.endAt());

		validateNoScheduleConflict(appointment.getDoctorAuthUserId(), appointment.getPatientId(), request.startAt(),
				request.endAt(), appointmentId);

		appointment.setStartAt(request.startAt());
		appointment.setEndAt(request.endAt());

		Appointment savedAppointment = appointmentRepository.saveAndFlush(appointment);

		publishNotification(OutboxEventType.APPOINTMENT_RESCHEDULED, savedAppointment, notificationContext);

		return appointmentMapper.toResponse(savedAppointment);
	}

	// ---------------------------------------------------------
	// CANCEL APPOINTMENT
	// ---------------------------------------------------------

	@Transactional
	public AppointmentResponse cancelAppointment(Long appointmentId, CancelAppointmentRequest request,
			AppointmentNotificationContext notificationContext) {

		validatePositiveId(appointmentId, "Appointment ID");

		if (request == null || request.cancellationReason() == null || request.cancellationReason().isBlank()) {

			throw new InvalidAppointmentRequestException("Cancellation reason is required.");
		}

		Appointment initialAppointment = findAppointment(appointmentId);

		validateNotificationContext(notificationContext, initialAppointment.getPatientId(),
				initialAppointment.getDoctorAuthUserId());

		scheduleLockService.acquireLocks(initialAppointment.getDoctorAuthUserId(), initialAppointment.getPatientId());

		Appointment appointment = findAppointmentForUpdate(appointmentId);

		validateUnchangedParticipants(initialAppointment, appointment, "cancellation");

		validateNotificationContext(notificationContext, appointment.getPatientId(), appointment.getDoctorAuthUserId());

		validateCancellableStatus(appointment);

		appointment.setStatus(AppointmentStatus.CANCELLED);
		appointment.setCancellationReason(request.cancellationReason().trim());
		appointment.setCancelledAt(Instant.now());

		Appointment savedAppointment = appointmentRepository.saveAndFlush(appointment);

		publishNotification(OutboxEventType.APPOINTMENT_CANCELLED, savedAppointment, notificationContext);

		return appointmentMapper.toResponse(savedAppointment);
	}

	// ---------------------------------------------------------
	// UPDATE APPOINTMENT STATUS
	// ---------------------------------------------------------

	@Transactional
	public AppointmentResponse updateAppointmentStatus(Long appointmentId, AppointmentStatus newStatus,
			AppointmentNotificationContext notificationContext) {

		validatePositiveId(appointmentId, "Appointment ID");

		Appointment initialAppointment = findAppointment(appointmentId);

		if (newStatus == AppointmentStatus.CANCELLED) {
			validateNotificationContext(notificationContext, initialAppointment.getPatientId(),
					initialAppointment.getDoctorAuthUserId());
		}

		scheduleLockService.acquireLocks(initialAppointment.getDoctorAuthUserId(), initialAppointment.getPatientId());

		Appointment appointment = findAppointmentForUpdate(appointmentId);

		validateUnchangedParticipants(initialAppointment, appointment, "status update");

		if (newStatus == AppointmentStatus.CANCELLED) {
			validateNotificationContext(notificationContext, appointment.getPatientId(),
					appointment.getDoctorAuthUserId());
		}

		validateStatusTransition(appointment.getStatus(), newStatus);

		appointment.setStatus(newStatus);

		if (newStatus == AppointmentStatus.CANCELLED) {
			appointment.setCancelledAt(Instant.now());
		}

		Appointment savedAppointment = appointmentRepository.saveAndFlush(appointment);

		if (newStatus == AppointmentStatus.CANCELLED) {
			publishNotification(OutboxEventType.APPOINTMENT_CANCELLED, savedAppointment, notificationContext);
		}

		return appointmentMapper.toResponse(savedAppointment);
	}

	// ---------------------------------------------------------
	// OUTBOX EVENT CREATION
	// ---------------------------------------------------------

	private void publishNotification(OutboxEventType eventType, Appointment appointment,
			AppointmentNotificationContext context) {

		AppointmentNotificationPayload payload = new AppointmentNotificationPayload(context.recipientEmail(),
				context.patientName(), context.doctorName(), appointment.getStartAt().toString(),
				appointment.getReason(), "APPT-" + appointment.getId(), appointment.getCancellationReason());

		outboxEventPublisher.publish(eventType, appointment.getId(), payload);
	}

	private void validateNotificationContext(AppointmentNotificationContext context, Long patientId,
			Long doctorAuthUserId) {

		if (context == null) {
			throw new InvalidAppointmentRequestException("Appointment notification participant context is required.");
		}

		if (!patientId.equals(context.patientId()) || !doctorAuthUserId.equals(context.doctorAuthUserId())) {

			throw new InvalidAppointmentOperationException(
					"Notification participant context does not match the appointment participants.");
		}

		if (context.recipientEmail() == null || context.recipientEmail().isBlank() || context.patientName() == null
				|| context.patientName().isBlank() || context.doctorName() == null || context.doctorName().isBlank()) {

			throw new InvalidAppointmentRequestException(
					"Appointment notification participant details are incomplete.");
		}
	}

	// ---------------------------------------------------------
	// ACTIVE DOCTOR-PATIENT ASSIGNMENT
	// ---------------------------------------------------------

	@Transactional(readOnly = true)
	public boolean hasActiveDoctorPatientAssignment(Long patientId, Long doctorAuthUserId) {

		validatePositiveId(patientId, "Patient ID");
		validatePositiveId(doctorAuthUserId, "Doctor user ID");

		return appointmentRepository.existsActiveDoctorPatientAssignment(patientId, doctorAuthUserId,
				ACTIVE_ASSIGNMENT_STATUSES);
	}

	// ---------------------------------------------------------
	// SCHEDULE CONFLICT VALIDATION
	// ---------------------------------------------------------

	private void validateNoScheduleConflict(Long doctorAuthUserId, Long patientId, Instant requestedStart,
			Instant requestedEnd, Long excludedAppointmentId) {

		boolean doctorConflict;
		boolean patientConflict;

		if (excludedAppointmentId == null) {

			doctorConflict = appointmentRepository.existsDoctorScheduleConflict(doctorAuthUserId, BLOCKING_STATUSES,
					requestedStart, requestedEnd);

			patientConflict = appointmentRepository.existsPatientScheduleConflict(patientId, BLOCKING_STATUSES,
					requestedStart, requestedEnd);

		} else {

			doctorConflict = appointmentRepository.existsDoctorScheduleConflictExcluding(doctorAuthUserId,
					BLOCKING_STATUSES, requestedStart, requestedEnd, excludedAppointmentId);

			patientConflict = appointmentRepository.existsPatientScheduleConflictExcluding(patientId, BLOCKING_STATUSES,
					requestedStart, requestedEnd, excludedAppointmentId);
		}

		if (doctorConflict) {
			throw new AppointmentConflictException("Doctor already has an appointment during the requested time.");
		}

		if (patientConflict) {
			throw new AppointmentConflictException("Patient already has an appointment during the requested time.");
		}
	}

	// ---------------------------------------------------------
	// TIME VALIDATION
	// ---------------------------------------------------------

	private void validateAppointmentTime(Instant startAt, Instant endAt) {

		if (startAt == null || endAt == null) {
			throw new InvalidAppointmentTimeException("Appointment start and end times are required.");
		}

		if (!endAt.isAfter(startAt)) {
			throw new InvalidAppointmentTimeException("Appointment end time must be after start time.");
		}

		if (!startAt.isAfter(Instant.now())) {
			throw new InvalidAppointmentTimeException("Appointment must be scheduled in the future.");
		}
	}

	// ---------------------------------------------------------
	// STATUS VALIDATION
	// ---------------------------------------------------------

	private void validateReschedulableStatus(Appointment appointment) {

		if (appointment.getStatus() != AppointmentStatus.SCHEDULED
				&& appointment.getStatus() != AppointmentStatus.CONFIRMED) {

			throw new InvalidAppointmentOperationException(
					"Only scheduled or confirmed appointments can be rescheduled.");
		}
	}

	private void validateCancellableStatus(Appointment appointment) {

		if (appointment.getStatus() != AppointmentStatus.SCHEDULED
				&& appointment.getStatus() != AppointmentStatus.CONFIRMED) {

			throw new InvalidAppointmentOperationException(
					"Only scheduled or confirmed appointments can be cancelled.");
		}
	}

	private void validateStatusTransition(AppointmentStatus currentStatus, AppointmentStatus newStatus) {

		if (newStatus == null || currentStatus == newStatus) {
			throw new InvalidAppointmentOperationException("Invalid appointment status transition.");
		}

		boolean validTransition = switch (currentStatus) {

		case SCHEDULED -> newStatus == AppointmentStatus.CONFIRMED || newStatus == AppointmentStatus.CANCELLED
				|| newStatus == AppointmentStatus.NO_SHOW;

		case CONFIRMED -> newStatus == AppointmentStatus.COMPLETED || newStatus == AppointmentStatus.CANCELLED
				|| newStatus == AppointmentStatus.NO_SHOW;

		case COMPLETED, CANCELLED, NO_SHOW -> false;
		};

		if (!validTransition) {
			throw new InvalidAppointmentOperationException(
					"Cannot change appointment status from " + currentStatus + " to " + newStatus);
		}
	}

	// ---------------------------------------------------------
	// APPOINTMENT RETRIEVAL HELPERS
	// ---------------------------------------------------------

	private Appointment findAppointment(Long appointmentId) {

		validatePositiveId(appointmentId, "Appointment ID");

		return appointmentRepository.findById(appointmentId)
				.orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
	}

	private Appointment findAppointmentForUpdate(Long appointmentId) {

		return appointmentRepository.findByIdForUpdate(appointmentId)
				.orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
	}

	private void validateUnchangedParticipants(Appointment initialAppointment, Appointment lockedAppointment,
			String operation) {

		if (!lockedAppointment.getDoctorAuthUserId().equals(initialAppointment.getDoctorAuthUserId())
				|| !lockedAppointment.getPatientId().equals(initialAppointment.getPatientId())) {

			throw new InvalidAppointmentOperationException(
					"Appointment participants changed during " + operation + ".");
		}
	}

	// ---------------------------------------------------------
	// ID VALIDATION
	// ---------------------------------------------------------

	private void validatePositiveId(Long id, String fieldName) {

		if (id == null || id <= 0) {
			throw new InvalidAppointmentRequestException(fieldName + " must be a positive number.");
		}
	}
}