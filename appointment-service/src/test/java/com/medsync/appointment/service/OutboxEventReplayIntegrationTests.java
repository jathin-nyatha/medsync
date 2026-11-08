
package com.medsync.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.entity.OutboxEventStatus;
import com.medsync.appointment.entity.OutboxEventType;
import com.medsync.appointment.repository.OutboxEventRepository;
import com.medsync.appointment.service.OutboxEventReplayService.ReplayResult;

@SpringBootTest
class OutboxEventReplayIntegrationTests {

	private static final String TEST_PREFIX = "it-outbox-replay-" + UUID.randomUUID() + "-";

	private static final String PAYLOAD = """
			{
			  "recipientEmail": "aarav.sharma@example.com",
			  "patientName": "Aarav Sharma",
			  "doctorName": "Dr. Meera Nair",
			  "appointmentDateTime": "2026-11-10T10:30:00Z",
			  "reason": "Annual health consultation",
			  "appointmentReference": "APPT-987654321",
			  "cancellationReason": null
			}
			""";

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private OutboxEventReplayService replayService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM outbox_events WHERE event_id LIKE ?", TEST_PREFIX + "%");
	}

	@Test
	void replayFailedEventPreservesIdentityAndPayloadAndResetsRetryState() {

		OutboxEvent event = saveEvent("successful-replay");

		jdbcTemplate.update("""
				UPDATE outbox_events
				SET
				    status = 'FAILED',
				    attempts = 5,
				    next_attempt_at = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR),
				    locked_until = NULL,
				    last_error = 'Invalid notification payload',
				    processed_at = NULL
				WHERE event_id = ?
				""", event.getEventId());

		OutboxEvent beforeReplay = findEvent(event.getEventId());

		ReplayResult result = replayService.replay(event.getEventId());

		OutboxEvent afterReplay = findEvent(event.getEventId());

		assertThat(result).isEqualTo(ReplayResult.REPLAYED);

		assertThat(afterReplay.getEventId()).isEqualTo(beforeReplay.getEventId());
		assertThat(afterReplay.getEventType()).isEqualTo(beforeReplay.getEventType());
		assertThat(afterReplay.getAppointmentId()).isEqualTo(beforeReplay.getAppointmentId());
		assertThat(afterReplay.getPayload()).isEqualTo(beforeReplay.getPayload());

		assertThat(afterReplay.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(afterReplay.getAttempts()).isZero();
		assertThat(afterReplay.getNextAttemptAt()).isNotNull();
		assertThat(afterReplay.getNextAttemptAt()).isBeforeOrEqualTo(Instant.now().plusSeconds(5));
		assertThat(afterReplay.getLockedUntil()).isNull();
		assertThat(afterReplay.getLastError()).isNull();
		assertThat(afterReplay.getProcessedAt()).isNull();
		assertThat(afterReplay.getVersion()).isGreaterThan(beforeReplay.getVersion());
	}

	@Test
	void replayRejectsEventThatIsNotFailed() {

		OutboxEvent event = saveEvent("not-failed");

		ReplayResult result = replayService.replay(event.getEventId());

		OutboxEvent reloadedEvent = findEvent(event.getEventId());

		assertThat(result).isEqualTo(ReplayResult.NOT_FAILED);
		assertThat(reloadedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
		assertThat(reloadedEvent.getAttempts()).isZero();
	}

	@Test
	void replayReturnsNotFoundForUnknownEvent() {

		ReplayResult result = replayService.replay(TEST_PREFIX + "missing");

		assertThat(result).isEqualTo(ReplayResult.NOT_FOUND);
	}

	@Test
	void concurrentReplayRequestsCanRequeueFailedEventOnlyOnce() throws Exception {

		OutboxEvent event = saveEvent("concurrent-replay");

		jdbcTemplate.update("""
				UPDATE outbox_events
				SET
				    status = 'FAILED',
				    attempts = 5,
				    last_error = 'Permanent failure',
				    locked_until = NULL,
				    processed_at = NULL
				WHERE event_id = ?
				""", event.getEventId());

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<ReplayResult> first = executor.submit(() -> {
				ready.countDown();
				start.await();
				return replayService.replay(event.getEventId());
			});

			Future<ReplayResult> second = executor.submit(() -> {
				ready.countDown();
				start.await();
				return replayService.replay(event.getEventId());
			});

			assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
			start.countDown();

			ReplayResult firstResult = first.get();
			ReplayResult secondResult = second.get();

			assertThat(java.util.List.of(firstResult, secondResult)).containsExactlyInAnyOrder(ReplayResult.REPLAYED,
					ReplayResult.NOT_FAILED);

			OutboxEvent reloadedEvent = findEvent(event.getEventId());

			assertThat(reloadedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
			assertThat(reloadedEvent.getAttempts()).isZero();
		} finally {
			executor.shutdownNow();
		}
	}

	private OutboxEvent saveEvent(String suffix) {

		OutboxEvent event = new OutboxEvent(TEST_PREFIX + suffix, OutboxEventType.APPOINTMENT_CONFIRMATION, 987654321L,
				PAYLOAD);

		event.setNextAttemptAt(Instant.now().minusSeconds(5));

		return outboxEventRepository.saveAndFlush(event);
	}

	private OutboxEvent findEvent(String eventId) {

		return outboxEventRepository.findByEventId(eventId)
				.orElseThrow(() -> new AssertionError("Expected outbox event to exist: " + eventId));
	}
}
