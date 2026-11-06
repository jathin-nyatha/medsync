
package com.medsync.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medsync.appointment.dto.CancelAppointmentRequest;
import com.medsync.appointment.dto.CreateAppointmentRequest;
import com.medsync.appointment.dto.RescheduleAppointmentRequest;
import com.medsync.appointment.entity.Appointment;
import com.medsync.appointment.entity.AppointmentStatus;
import com.medsync.appointment.exception.AppointmentConflictException;
import com.medsync.appointment.exception.AppointmentNotFoundException;
import com.medsync.appointment.exception.InvalidAppointmentOperationException;
import com.medsync.appointment.exception.InvalidAppointmentRequestException;
import com.medsync.appointment.exception.InvalidAppointmentTimeException;
import com.medsync.appointment.repository.AppointmentRepository;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTests {

	@Mock
	private AppointmentRepository appointmentRepository;

	@Mock
	private ScheduleLockService scheduleLockService;

	private AppointmentService appointmentService;

	private static final Long APPOINTMENT_ID = 1001L;
	private static final Long PATIENT_ID = 101L;
	private static final Long DOCTOR_ID = 25L;
	private static final Long CREATOR_ID = 10L;

	private Instant startAt;
	private Instant endAt;

	@BeforeEach
	void setUp() {

		appointmentService = new AppointmentService(appointmentRepository, new AppointmentMapper(),
				scheduleLockService);

		startAt = Instant.now().plusSeconds(86_400);
		endAt = startAt.plusSeconds(1_800);
	}

	// ---------------------------------------------------------
	// CREATE APPOINTMENT
	// ---------------------------------------------------------

	@Test
	void createAppointmentSuccessfully() {

		CreateAppointmentRequest request = createRequest(startAt, endAt);

		when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> {
			Appointment appointment = invocation.getArgument(0);
			appointment.setId(APPOINTMENT_ID);
			return appointment;
		});

		var response = appointmentService.createAppointment(request, CREATOR_ID);

		assertThat(response.id()).isEqualTo(APPOINTMENT_ID);
		assertThat(response.patientId()).isEqualTo(PATIENT_ID);
		assertThat(response.doctorAuthUserId()).isEqualTo(DOCTOR_ID);
		assertThat(response.createdByAuthUserId()).isEqualTo(CREATOR_ID);
		assertThat(response.status()).isEqualTo(AppointmentStatus.SCHEDULED);
		assertThat(response.reason()).isEqualTo("General consultation");

		verify(scheduleLockService).acquireLocks(DOCTOR_ID, PATIENT_ID);
		verify(appointmentRepository).saveAndFlush(any(Appointment.class));
	}

	@Test
	void createAppointmentRejectsNullRequest() {

		assertThatThrownBy(() -> appointmentService.createAppointment(null, CREATOR_ID))
				.isInstanceOf(InvalidAppointmentRequestException.class);

		verifyNoInteractions(appointmentRepository, scheduleLockService);
	}

	@Test
	void createAppointmentRejectsInvalidPatientId() {

		CreateAppointmentRequest request = new CreateAppointmentRequest(0L, DOCTOR_ID, startAt, endAt,
				"General consultation");

		assertThatThrownBy(() -> appointmentService.createAppointment(request, CREATOR_ID))
				.isInstanceOf(InvalidAppointmentRequestException.class);

		verifyNoInteractions(scheduleLockService);
	}

	@Test
	void createAppointmentRejectsInvalidDoctorId() {

		CreateAppointmentRequest request = new CreateAppointmentRequest(PATIENT_ID, -1L, startAt, endAt,
				"General consultation");

		assertThatThrownBy(() -> appointmentService.createAppointment(request, CREATOR_ID))
				.isInstanceOf(InvalidAppointmentRequestException.class);

		verifyNoInteractions(scheduleLockService);
	}

	@Test
	void createAppointmentRejectsInvalidCreatorId() {

		assertThatThrownBy(() -> appointmentService.createAppointment(createRequest(startAt, endAt), 0L))
				.isInstanceOf(InvalidAppointmentRequestException.class);

		verifyNoInteractions(scheduleLockService);
	}

	@Test
	void createAppointmentRejectsPastStartTime() {

		Instant pastStart = Instant.now().minusSeconds(3_600);
		Instant pastEnd = pastStart.plusSeconds(1_800);

		assertThatThrownBy(() -> appointmentService.createAppointment(createRequest(pastStart, pastEnd), CREATOR_ID))
				.isInstanceOf(InvalidAppointmentTimeException.class);

		verifyNoInteractions(scheduleLockService);
	}

	@Test
	void createAppointmentRejectsEndTimeBeforeStartTime() {

		assertThatThrownBy(() -> appointmentService.createAppointment(createRequest(startAt, startAt.minusSeconds(60)),
				CREATOR_ID)).isInstanceOf(InvalidAppointmentTimeException.class);

		verifyNoInteractions(scheduleLockService);
	}

	@Test
	void createAppointmentRejectsDoctorScheduleConflict() {

		when(appointmentRepository.existsDoctorScheduleConflict(eq(DOCTOR_ID), anyCollection(), eq(startAt), eq(endAt)))
				.thenReturn(true);

		assertThatThrownBy(() -> appointmentService.createAppointment(createRequest(startAt, endAt), CREATOR_ID))
				.isInstanceOf(AppointmentConflictException.class).hasMessageContaining("Doctor");

		verify(scheduleLockService).acquireLocks(DOCTOR_ID, PATIENT_ID);
		verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
	}

	@Test
	void createAppointmentRejectsPatientScheduleConflict() {

		when(appointmentRepository.existsPatientScheduleConflict(eq(PATIENT_ID), anyCollection(), eq(startAt),
				eq(endAt))).thenReturn(true);

		assertThatThrownBy(() -> appointmentService.createAppointment(createRequest(startAt, endAt), CREATOR_ID))
				.isInstanceOf(AppointmentConflictException.class).hasMessageContaining("Patient");

		verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
	}

	// ---------------------------------------------------------
	// RETRIEVE APPOINTMENT
	// ---------------------------------------------------------

	@Test
	void getAppointmentByIdSuccessfully() {

		Appointment appointment = createAppointment(APPOINTMENT_ID, AppointmentStatus.SCHEDULED);

		when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		var response = appointmentService.getAppointmentById(APPOINTMENT_ID);

		assertThat(response.id()).isEqualTo(APPOINTMENT_ID);
		assertThat(response.patientId()).isEqualTo(PATIENT_ID);
	}

	@Test
	void getAppointmentByIdThrowsWhenNotFound() {

		when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> appointmentService.getAppointmentById(APPOINTMENT_ID))
				.isInstanceOf(AppointmentNotFoundException.class);
	}

	@Test
	void getPatientAppointmentSuccessfully() {

		Appointment appointment = createAppointment(APPOINTMENT_ID, AppointmentStatus.SCHEDULED);

		when(appointmentRepository.findByIdAndPatientId(APPOINTMENT_ID, PATIENT_ID))
				.thenReturn(Optional.of(appointment));

		var response = appointmentService.getPatientAppointment(APPOINTMENT_ID, PATIENT_ID);

		assertThat(response.patientId()).isEqualTo(PATIENT_ID);
	}

	@Test
	void getDoctorAppointmentSuccessfully() {

		Appointment appointment = createAppointment(APPOINTMENT_ID, AppointmentStatus.SCHEDULED);

		when(appointmentRepository.findByIdAndDoctorAuthUserId(APPOINTMENT_ID, DOCTOR_ID))
				.thenReturn(Optional.of(appointment));

		var response = appointmentService.getDoctorAppointment(APPOINTMENT_ID, DOCTOR_ID);

		assertThat(response.doctorAuthUserId()).isEqualTo(DOCTOR_ID);
	}

	// ---------------------------------------------------------
	// RESCHEDULE APPOINTMENT
	// ---------------------------------------------------------

	@Test
	void rescheduleAppointmentSuccessfully() {

		Appointment appointment = createAppointment(APPOINTMENT_ID, AppointmentStatus.SCHEDULED);

		Instant newStart = Instant.now().plusSeconds(172_800);
		Instant newEnd = newStart.plusSeconds(1_800);

		when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		when(appointmentRepository.saveAndFlush(any(Appointment.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		var response = appointmentService.rescheduleAppointment(APPOINTMENT_ID,
				new RescheduleAppointmentRequest(newStart, newEnd));

		assertThat(response.startAt()).isEqualTo(newStart);
		assertThat(response.endAt()).isEqualTo(newEnd);

		verify(scheduleLockService).acquireLocks(DOCTOR_ID, PATIENT_ID);

		verify(appointmentRepository).existsDoctorScheduleConflictExcluding(eq(DOCTOR_ID), anyCollection(),
				eq(newStart), eq(newEnd), eq(APPOINTMENT_ID));

		verify(appointmentRepository).existsPatientScheduleConflictExcluding(eq(PATIENT_ID), anyCollection(),
				eq(newStart), eq(newEnd), eq(APPOINTMENT_ID));
	}

	@Test
	void rescheduleAppointmentRejectsCompletedAppointment() {

		Appointment appointment = createAppointment(APPOINTMENT_ID, AppointmentStatus.COMPLETED);

		when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		assertThatThrownBy(() -> appointmentService.rescheduleAppointment(APPOINTMENT_ID,
				new RescheduleAppointmentRequest(startAt, endAt)))
				.isInstanceOf(InvalidAppointmentOperationException.class);

		verify(scheduleLockService).acquireLocks(DOCTOR_ID, PATIENT_ID);
		verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
	}

	@Test
	void rescheduleAppointmentRejectsScheduleConflict() {

		Appointment appointment = createAppointment(APPOINTMENT_ID, AppointmentStatus.SCHEDULED);

		when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		when(appointmentRepository.existsDoctorScheduleConflictExcluding(eq(DOCTOR_ID), anyCollection(), eq(startAt),
				eq(endAt), eq(APPOINTMENT_ID))).thenReturn(true);

		assertThatThrownBy(() -> appointmentService.rescheduleAppointment(APPOINTMENT_ID,
				new RescheduleAppointmentRequest(startAt, endAt))).isInstanceOf(AppointmentConflictException.class);

		verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
	}

	@Test
	void rescheduleAppointmentRejectsMissingAppointment() {

		when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> appointmentService.rescheduleAppointment(APPOINTMENT_ID,
				new RescheduleAppointmentRequest(startAt, endAt))).isInstanceOf(AppointmentNotFoundException.class);

		verifyNoInteractions(scheduleLockService);
	}

	// ---------------------------------------------------------
	// CANCEL APPOINTMENT
	// ---------------------------------------------------------

	@Test
	void cancelAppointmentSuccessfully() {

		Appointment appointment = createAppointment(APPOINTMENT_ID, AppointmentStatus.CONFIRMED);

		when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		when(appointmentRepository.saveAndFlush(any(Appointment.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		var response = appointmentService.cancelAppointment(APPOINTMENT_ID,
				new CancelAppointmentRequest("Patient requested cancellation"));

		assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
		assertThat(response.cancellationReason()).isEqualTo("Patient requested cancellation");
		assertThat(response.cancelledAt()).isNotNull();

		verify(scheduleLockService).acquireLocks(DOCTOR_ID, PATIENT_ID);
	}

	@Test
	void cancelAppointmentRejectsBlankCancellationReason() {

		assertThatThrownBy(
				() -> appointmentService.cancelAppointment(APPOINTMENT_ID, new CancelAppointmentRequest("   ")))
				.isInstanceOf(InvalidAppointmentRequestException.class);

		verifyNoInteractions(appointmentRepository, scheduleLockService);
	}

	@Test
	void cancelAppointmentRejectsCompletedAppointment() {

		Appointment appointment = createAppointment(APPOINTMENT_ID, AppointmentStatus.COMPLETED);

		when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		assertThatThrownBy(() -> appointmentService.cancelAppointment(APPOINTMENT_ID,
				new CancelAppointmentRequest("Patient requested cancellation")))
				.isInstanceOf(InvalidAppointmentOperationException.class);

		verify(scheduleLockService).acquireLocks(DOCTOR_ID, PATIENT_ID);
		verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
	}

	// ---------------------------------------------------------
	// UPDATE APPOINTMENT STATUS
	// ---------------------------------------------------------

	@Test
	void updateStatusFromScheduledToConfirmed() {

		Appointment appointment = createAppointment(APPOINTMENT_ID, AppointmentStatus.SCHEDULED);

		when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		when(appointmentRepository.saveAndFlush(any(Appointment.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		var response = appointmentService.updateAppointmentStatus(APPOINTMENT_ID, AppointmentStatus.CONFIRMED);

		assertThat(response.status()).isEqualTo(AppointmentStatus.CONFIRMED);
		verify(scheduleLockService).acquireLocks(DOCTOR_ID, PATIENT_ID);
	}

	@Test
	void updateStatusFromConfirmedToCompleted() {

		Appointment appointment = createAppointment(APPOINTMENT_ID, AppointmentStatus.CONFIRMED);

		when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		when(appointmentRepository.saveAndFlush(any(Appointment.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		var response = appointmentService.updateAppointmentStatus(APPOINTMENT_ID, AppointmentStatus.COMPLETED);

		assertThat(response.status()).isEqualTo(AppointmentStatus.COMPLETED);
	}

	@Test
	void updateStatusRejectsTransitionFromCancelled() {

		Appointment appointment = createAppointment(APPOINTMENT_ID, AppointmentStatus.CANCELLED);

		when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		assertThatThrownBy(
				() -> appointmentService.updateAppointmentStatus(APPOINTMENT_ID, AppointmentStatus.CONFIRMED))
				.isInstanceOf(InvalidAppointmentOperationException.class);

		verify(scheduleLockService).acquireLocks(DOCTOR_ID, PATIENT_ID);
		verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
	}

	@Test
	void updateStatusRejectsSameStatus() {

		Appointment appointment = createAppointment(APPOINTMENT_ID, AppointmentStatus.SCHEDULED);

		when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));

		assertThatThrownBy(
				() -> appointmentService.updateAppointmentStatus(APPOINTMENT_ID, AppointmentStatus.SCHEDULED))
				.isInstanceOf(InvalidAppointmentOperationException.class);

		verify(scheduleLockService).acquireLocks(DOCTOR_ID, PATIENT_ID);
		verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
	}

	// ---------------------------------------------------------
	// ACTIVE DOCTOR-PATIENT ASSIGNMENT
	// ---------------------------------------------------------

	@Test
	void hasActiveDoctorPatientAssignmentReturnsTrue() {

		when(appointmentRepository.existsActiveDoctorPatientAssignment(eq(PATIENT_ID), eq(DOCTOR_ID), anyCollection()))
				.thenReturn(true);

		assertThat(appointmentService.hasActiveDoctorPatientAssignment(PATIENT_ID, DOCTOR_ID)).isTrue();
	}

	@Test
	void hasActiveDoctorPatientAssignmentReturnsFalse() {

		when(appointmentRepository.existsActiveDoctorPatientAssignment(eq(PATIENT_ID), eq(DOCTOR_ID), anyCollection()))
				.thenReturn(false);

		assertThat(appointmentService.hasActiveDoctorPatientAssignment(PATIENT_ID, DOCTOR_ID)).isFalse();
	}

	@Test
	void hasActiveDoctorPatientAssignmentRejectsInvalidPatientId() {

		assertThatThrownBy(() -> appointmentService.hasActiveDoctorPatientAssignment(0L, DOCTOR_ID))
				.isInstanceOf(InvalidAppointmentRequestException.class);
	}

	// ---------------------------------------------------------
	// TEST DATA HELPERS
	// ---------------------------------------------------------

	private CreateAppointmentRequest createRequest(Instant start, Instant end) {

		return new CreateAppointmentRequest(PATIENT_ID, DOCTOR_ID, start, end, "General consultation");
	}

	private Appointment createAppointment(Long id, AppointmentStatus status) {

		Appointment appointment = new Appointment();

		appointment.setId(id);
		appointment.setPatientId(PATIENT_ID);
		appointment.setDoctorAuthUserId(DOCTOR_ID);
		appointment.setCreatedByAuthUserId(CREATOR_ID);
		appointment.setStartAt(startAt);
		appointment.setEndAt(endAt);
		appointment.setReason("General consultation");
		appointment.setStatus(status);

		return appointment;
	}
}
