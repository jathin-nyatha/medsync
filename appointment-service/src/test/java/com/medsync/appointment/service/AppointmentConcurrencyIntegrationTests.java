
package com.medsync.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.medsync.appointment.dto.AppointmentResponse;
import com.medsync.appointment.dto.CancelAppointmentRequest;
import com.medsync.appointment.dto.CreateAppointmentRequest;
import com.medsync.appointment.entity.AppointmentStatus;
import com.medsync.appointment.exception.AppointmentConflictException;
import com.medsync.appointment.repository.AppointmentRepository;

@SpringBootTest
class AppointmentConcurrencyIntegrationTests {

	@Autowired
	private AppointmentService appointmentService;

	@Autowired
	private AppointmentRepository appointmentRepository;

	private static final AtomicLong ID_GENERATOR = new AtomicLong(System.currentTimeMillis() * 1000);

	private final Set<Long> createdAppointmentIds = ConcurrentHashMap.newKeySet();

	@AfterEach
	void cleanUp() {

		if (!createdAppointmentIds.isEmpty()) {
			appointmentRepository.deleteAllById(createdAppointmentIds);
			createdAppointmentIds.clear();
		}
	}

	// ---------------------------------------------------------
	// CONCURRENT DOCTOR SCHEDULE CONFLICT
	// ---------------------------------------------------------

	@Test
	void concurrentBookingsForSameDoctorAllowOnlyOneBooking() throws Exception {

		Long doctorId = nextId();
		Long patientOne = nextId();
		Long patientTwo = nextId();

		Instant start = futureStart();
		Instant end = start.plus(Duration.ofMinutes(30));

		CreateAppointmentRequest requestOne = createRequest(patientOne, doctorId, start, end);

		CreateAppointmentRequest requestTwo = createRequest(patientTwo, doctorId, start, end);

		Set<Long> successfulIds = executeConcurrentBookings(requestOne, requestTwo);

		assertThat(successfulIds).hasSize(1);

		createdAppointmentIds.addAll(successfulIds);

		assertThat(appointmentRepository.count()).isGreaterThanOrEqualTo(1);
	}

	// ---------------------------------------------------------
	// CONCURRENT PATIENT SCHEDULE CONFLICT
	// ---------------------------------------------------------

	@Test
	void concurrentBookingsForSamePatientAllowOnlyOneBooking() throws Exception {

		Long patientId = nextId();
		Long doctorOne = nextId();
		Long doctorTwo = nextId();

		Instant start = futureStart();
		Instant end = start.plus(Duration.ofMinutes(30));

		CreateAppointmentRequest requestOne = createRequest(patientId, doctorOne, start, end);

		CreateAppointmentRequest requestTwo = createRequest(patientId, doctorTwo, start, end);

		Set<Long> successfulIds = executeConcurrentBookings(requestOne, requestTwo);

		assertThat(successfulIds).hasSize(1);

		createdAppointmentIds.addAll(successfulIds);
	}

	// ---------------------------------------------------------
	// BACK-TO-BACK APPOINTMENTS
	// ---------------------------------------------------------

	@Test
	void backToBackAppointmentsForSameDoctorAreAllowed() {

		Long doctorId = nextId();
		Long patientOne = nextId();
		Long patientTwo = nextId();

		Instant firstStart = futureStart();
		Instant firstEnd = firstStart.plus(Duration.ofMinutes(30));

		Instant secondStart = firstEnd;
		Instant secondEnd = secondStart.plus(Duration.ofMinutes(30));

		AppointmentResponse first = appointmentService
				.createAppointment(createRequest(patientOne, doctorId, firstStart, firstEnd), nextId());

		createdAppointmentIds.add(first.id());

		AppointmentResponse second = appointmentService
				.createAppointment(createRequest(patientTwo, doctorId, secondStart, secondEnd), nextId());

		createdAppointmentIds.add(second.id());

		assertThat(first.id()).isNotEqualTo(second.id());
		assertThat(first.status()).isEqualTo(AppointmentStatus.SCHEDULED);
		assertThat(second.status()).isEqualTo(AppointmentStatus.SCHEDULED);
	}

	// ---------------------------------------------------------
	// CANCELLED APPOINTMENT RELEASES SLOT
	// ---------------------------------------------------------

	@Test
	void cancelledAppointmentReleasesDoctorAndPatientTimeSlot() {

		Long doctorId = nextId();
		Long patientId = nextId();

		Instant start = futureStart();
		Instant end = start.plus(Duration.ofMinutes(30));

		AppointmentResponse original = appointmentService
				.createAppointment(createRequest(patientId, doctorId, start, end), nextId());

		createdAppointmentIds.add(original.id());

		AppointmentResponse cancelled = appointmentService.cancelAppointment(original.id(),
				new CancelAppointmentRequest("Patient requested cancellation"));

		assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED);

		AppointmentResponse replacement = appointmentService
				.createAppointment(createRequest(patientId, doctorId, start, end), nextId());

		createdAppointmentIds.add(replacement.id());

		assertThat(replacement.id()).isNotEqualTo(original.id());
		assertThat(replacement.status()).isEqualTo(AppointmentStatus.SCHEDULED);
	}

	// ---------------------------------------------------------
	// CONCURRENT EXECUTION HELPER
	// ---------------------------------------------------------

	private Set<Long> executeConcurrentBookings(CreateAppointmentRequest requestOne,
			CreateAppointmentRequest requestTwo)
			throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {

		ExecutorService executor = Executors.newFixedThreadPool(2);

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try {

			CompletableFuture<Long> first = CompletableFuture
					.supplyAsync(() -> attemptBooking(requestOne, ready, start), executor);

			CompletableFuture<Long> second = CompletableFuture
					.supplyAsync(() -> attemptBooking(requestTwo, ready, start), executor);

			boolean bothReady = ready.await(10, TimeUnit.SECONDS);

			if (!bothReady) {
				throw new IllegalStateException("Concurrent booking workers did not become ready.");
			}

			start.countDown();

			Long firstId = first.get(20, TimeUnit.SECONDS);
			Long secondId = second.get(20, TimeUnit.SECONDS);

			Set<Long> successfulIds = ConcurrentHashMap.newKeySet();

			if (firstId != null) {
				successfulIds.add(firstId);
			}

			if (secondId != null) {
				successfulIds.add(secondId);
			}

			return successfulIds;

		} finally {

			start.countDown();
			executor.shutdownNow();
			executor.awaitTermination(10, TimeUnit.SECONDS);
		}
	}

	private Long attemptBooking(CreateAppointmentRequest request, CountDownLatch ready, CountDownLatch start) {

		ready.countDown();

		try {

			if (!start.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Concurrent booking start signal timed out.");
			}

			AppointmentResponse response = appointmentService.createAppointment(request, nextId());

			return response.id();

		} catch (AppointmentConflictException exception) {

			return null;

		} catch (InterruptedException exception) {

			Thread.currentThread().interrupt();

			throw new IllegalStateException("Booking worker was interrupted.", exception);
		}
	}

	// ---------------------------------------------------------
	// TEST DATA HELPERS
	// ---------------------------------------------------------

	private CreateAppointmentRequest createRequest(Long patientId, Long doctorId, Instant start, Instant end) {

		return new CreateAppointmentRequest(patientId, doctorId, start, end, "Integration test consultation");
	}

	private Instant futureStart() {

		return Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
	}

	private Long nextId() {

		return ID_GENERATOR.incrementAndGet();
	}
}
