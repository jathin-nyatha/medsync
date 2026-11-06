package com.medsync.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.medsync.appointment.dto.AppointmentNotificationContext;
import com.medsync.appointment.dto.AppointmentNotificationPayload;
import com.medsync.appointment.dto.CreateAppointmentRequest;
import com.medsync.appointment.entity.OutboxEventType;
import com.medsync.appointment.repository.AppointmentRepository;

@SpringBootTest
class AppointmentOutboxTransactionIntegrationTests {

	@Autowired
	private AppointmentService appointmentService;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@MockitoBean
	private OutboxEventPublisher outboxEventPublisher;

	private static final AtomicLong ID_GENERATOR = new AtomicLong(System.currentTimeMillis() * 1000);

	@Test
	void appointmentCreationRollsBackWhenOutboxPublishingFails() {

		// Arrange: generate unique participant IDs.
		Long patientId = nextId();
		Long doctorId = nextId();
		Long creatorId = nextId();

		Instant startAt = Instant.now().plusSeconds(172_800).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

		Instant endAt = startAt.plusSeconds(1_800);

		CreateAppointmentRequest request = new CreateAppointmentRequest(patientId, doctorId, startAt, endAt,
				"Transactional outbox integration test");

		AppointmentNotificationContext context = new AppointmentNotificationContext(patientId, doctorId,
				"patient@example.com", "Aarav Sharma", "Dr. Meera Nair");

		// Simulate a failure while publishing the outbox event.
		doThrow(new IllegalStateException("Simulated outbox persistence failure")).when(outboxEventPublisher).publish(
				eq(OutboxEventType.APPOINTMENT_CONFIRMATION), anyLong(), any(AppointmentNotificationPayload.class));

		// Act + Assert: the service call must fail.
		assertThatThrownBy(() -> appointmentService.createAppointment(request, creatorId, context))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Simulated outbox persistence failure");

		// Assert: appointment creation must have rolled back.
		long appointmentCount = appointmentRepository.findByPatientId(patientId, Pageable.unpaged()).getTotalElements();

		assertThat(appointmentCount).as("Appointment must not persist when outbox publishing fails").isZero();
	}

	private Long nextId() {
		return ID_GENERATOR.incrementAndGet();
	}
}