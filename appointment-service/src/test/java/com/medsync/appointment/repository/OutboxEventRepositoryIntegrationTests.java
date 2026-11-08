
package com.medsync.appointment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.entity.OutboxEventStatus;
import com.medsync.appointment.entity.OutboxEventType;
import com.medsync.appointment.properties.OutboxDispatcherProperties;
import com.medsync.appointment.service.OutboxEventClaimService;
import com.medsync.appointment.service.OutboxEventStateService;

@SpringBootTest
class OutboxEventRepositoryIntegrationTests {

	private static final String TEST_PREFIX = "it-outbox-" + UUID.randomUUID() + "-";

	private static final Duration TEST_LEASE_DURATION = Duration.ofMinutes(2);

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private OutboxEventClaimService outboxEventClaimService;

	@Autowired
	private OutboxEventStateService outboxEventStateService;

	@Autowired
	private OutboxDispatcherProperties properties;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM outbox_events WHERE event_id LIKE ?", TEST_PREFIX + "%");
	}

	@Test
	void claimBatchClaimsEligiblePendingEvent() {

		OutboxEvent savedEvent = savePendingEvent("claim-pending");

		List<OutboxEvent> claimedEvents = outboxEventClaimService.claimBatch(100, TEST_LEASE_DURATION);

		OutboxEvent claimedEvent = findEvent(savedEvent.getEventId());

		assertThat(claimedEvents).extracting(OutboxEvent::getEventId).contains(savedEvent.getEventId());

		assertThat(claimedEvent.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);

		assertThat(claimedEvent.getAttempts()).isEqualTo(1);
		assertThat(claimedEvent.getLockedUntil()).isAfter(Instant.now());
		assertThat(claimedEvent.getVersion()).isGreaterThan(savedEvent.getVersion());
	}

	@Test
	void claimBatchDoesNotClaimSameEventTwiceWhileLeaseIsActive() {

		OutboxEvent savedEvent = savePendingEvent("claim-once");

		List<OutboxEvent> firstBatch = outboxEventClaimService.claimBatch(100, TEST_LEASE_DURATION);

		assertThat(firstBatch).extracting(OutboxEvent::getEventId).contains(savedEvent.getEventId());

		List<OutboxEvent> secondBatch = outboxEventClaimService.claimBatch(100, TEST_LEASE_DURATION);

		assertThat(secondBatch).extracting(OutboxEvent::getEventId).doesNotContain(savedEvent.getEventId());

		OutboxEvent reloadedEvent = findEvent(savedEvent.getEventId());

		assertThat(reloadedEvent.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);

		assertThat(reloadedEvent.getAttempts()).isEqualTo(1);
	}

	@Test
	void claimBatchReclaimsProcessingEventAfterLeaseExpires() {

		OutboxEvent event = saveProcessingEvent("expired-lease", Instant.now().minusSeconds(30), 1);

		List<OutboxEvent> claimedEvents = outboxEventClaimService.claimBatch(100, TEST_LEASE_DURATION);

		OutboxEvent reloadedEvent = findEvent(event.getEventId());

		assertThat(claimedEvents).extracting(OutboxEvent::getEventId).contains(event.getEventId());

		assertThat(reloadedEvent.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);

		assertThat(reloadedEvent.getAttempts()).isEqualTo(2);
		assertThat(reloadedEvent.getLockedUntil()).isAfter(Instant.now());
	}

	@Test
	void futurePendingEventIsNotEligibleForClaiming() {

		OutboxEvent event = savePendingEvent("future-retry", Instant.now().plusSeconds(3600));

		List<Long> eligibleIds = outboxEventRepository.findEligibleEventIds(Instant.now(), 100);

		assertThat(eligibleIds).doesNotContain(event.getId());

		OutboxEvent reloadedEvent = findEvent(event.getEventId());

		assertThat(reloadedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);

		assertThat(reloadedEvent.getAttempts()).isZero();
	}

	@Test
	void claimBatchAllowsOnlyOneWorkerToClaimSameEventConcurrently() throws Exception {

		OutboxEvent savedEvent = savePendingEvent("concurrent-claim");

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<List<OutboxEvent>> firstWorker = executor.submit(() -> {
				ready.countDown();

				if (!start.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("Timed out waiting to start first worker");
				}

				return outboxEventClaimService.claimBatch(100, TEST_LEASE_DURATION);
			});

			Future<List<OutboxEvent>> secondWorker = executor.submit(() -> {
				ready.countDown();

				if (!start.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("Timed out waiting to start second worker");
				}

				return outboxEventClaimService.claimBatch(100, TEST_LEASE_DURATION);
			});

			assertThat(ready.await(5, TimeUnit.SECONDS)).as("Both workers should be ready before starting").isTrue();

			start.countDown();

			List<OutboxEvent> firstResults = firstWorker.get(15, TimeUnit.SECONDS);

			List<OutboxEvent> secondResults = secondWorker.get(15, TimeUnit.SECONDS);

			long successfulClaims = Stream.concat(firstResults.stream(), secondResults.stream())
					.filter(event -> event.getEventId().equals(savedEvent.getEventId())).count();

			assertThat(successfulClaims).as("Exactly one worker must claim the event").isEqualTo(1);

			OutboxEvent reloadedEvent = findEvent(savedEvent.getEventId());

			assertThat(reloadedEvent.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);

			assertThat(reloadedEvent.getAttempts()).as("Concurrent workers must not increment attempts twice")
					.isEqualTo(1);

			assertThat(reloadedEvent.getLockedUntil()).isAfter(Instant.now());

		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void markPublishedUpdatesCurrentClaim() {

		OutboxEvent event = savePendingEvent("publish");

		OutboxEvent claimedEvent = claimEvent(event.getEventId());

		Long claimedVersion = claimedEvent.getVersion();

		boolean updated = outboxEventStateService.markPublished(claimedEvent);

		OutboxEvent publishedEvent = findEvent(event.getEventId());

		assertThat(updated).isTrue();

		assertThat(publishedEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);

		assertThat(publishedEvent.getProcessedAt()).isNotNull();
		assertThat(publishedEvent.getLockedUntil()).isNull();
		assertThat(publishedEvent.getLastError()).isNull();

		assertThat(publishedEvent.getVersion()).isEqualTo(claimedVersion + 1);
	}

	@Test
	void markPublishedRejectsStaleClaimVersion() {

		OutboxEvent event = savePendingEvent("stale-publish");

		OutboxEvent staleClaim = claimEvent(event.getEventId());

		Long staleVersion = staleClaim.getVersion();

		jdbcTemplate.update("""
				UPDATE outbox_events
				SET version = version + 1
				WHERE event_id = ?
				""", event.getEventId());

		boolean updated = outboxEventStateService.markPublished(staleClaim);

		OutboxEvent reloadedEvent = findEvent(event.getEventId());

		assertThat(updated).isFalse();

		assertThat(reloadedEvent.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);

		assertThat(reloadedEvent.getVersion()).isEqualTo(staleVersion + 1);

		assertThat(reloadedEvent.getProcessedAt()).isNull();
	}

	@Test
	void recordFailureReschedulesRetryableEvent() {

		OutboxEvent event = savePendingEvent("retry");

		OutboxEvent claimedEvent = claimEvent(event.getEventId());

		boolean updated = outboxEventStateService.recordFailure(claimedEvent,
				new RuntimeException("Notification Service unavailable"), true, properties);

		OutboxEvent reloadedEvent = findEvent(event.getEventId());

		assertThat(updated).isTrue();

		assertThat(reloadedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);

		assertThat(reloadedEvent.getAttempts()).isEqualTo(1);
		assertThat(reloadedEvent.getNextAttemptAt()).isAfter(Instant.now());

		assertThat(reloadedEvent.getLockedUntil()).isNull();

		assertThat(reloadedEvent.getLastError()).contains("Notification Service unavailable");

		assertThat(reloadedEvent.getProcessedAt()).isNull();
	}

	@Test
	void recordFailureMarksNonRetryableEventAsFailed() {

		OutboxEvent event = savePendingEvent("permanent-failure");

		OutboxEvent claimedEvent = claimEvent(event.getEventId());

		boolean updated = outboxEventStateService.recordFailure(claimedEvent,
				new IllegalArgumentException("Invalid notification payload"), false, properties);

		OutboxEvent reloadedEvent = findEvent(event.getEventId());

		assertThat(updated).isTrue();

		assertThat(reloadedEvent.getStatus()).isEqualTo(OutboxEventStatus.FAILED);

		assertThat(reloadedEvent.getAttempts()).isEqualTo(1);
		assertThat(reloadedEvent.getLockedUntil()).isNull();

		assertThat(reloadedEvent.getLastError()).contains("Invalid notification payload");

		assertThat(reloadedEvent.getProcessedAt()).isNull();
	}

	@Test
	void renewLeaseExtendsActiveLeaseAndAdvancesClaimVersion() {

		OutboxEvent event = savePendingEvent("renew-active");

		OutboxEvent claimedEvent = claimEvent(event.getEventId());

		Instant originalLeaseUntil = claimedEvent.getLockedUntil();
		Long originalVersion = claimedEvent.getVersion();

		Instant renewalStartedAt = Instant.now();

		boolean renewed = outboxEventStateService.renewLease(claimedEvent, TEST_LEASE_DURATION);

		Instant renewalCompletedAt = Instant.now();

		OutboxEvent reloadedEvent = findEvent(event.getEventId());

		assertThat(renewed).isTrue();

		assertThat(reloadedEvent.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);

		assertThat(reloadedEvent.getLockedUntil()).isAfter(originalLeaseUntil);

		// MySQL DATETIME(6) and JDBC may round nanoseconds at the
		// microsecond boundary. Verify a safe time window instead
		// of requiring exact Instant equality.
		assertThat(reloadedEvent.getLockedUntil()).isBetween(renewalStartedAt.plus(TEST_LEASE_DURATION).minusSeconds(1),
				renewalCompletedAt.plus(TEST_LEASE_DURATION).plusSeconds(1));

		assertThat(reloadedEvent.getVersion()).isEqualTo(originalVersion + 1);

		assertThat(claimedEvent.getVersion()).isEqualTo(originalVersion + 1);

		assertThat(claimedEvent.getLockedUntil()).isEqualTo(reloadedEvent.getLockedUntil());
	}

	@Test
	void renewLeaseRejectsExpiredLease() {

		OutboxEvent event = savePendingEvent("renew-expired");

		OutboxEvent claimedEvent = claimEvent(event.getEventId());

		jdbcTemplate.update("""
				UPDATE outbox_events
				SET locked_until = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 SECOND)
				WHERE id = ?
				""", claimedEvent.getId());
		boolean renewed = outboxEventStateService.renewLease(claimedEvent, TEST_LEASE_DURATION);

		OutboxEvent reloadedEvent = findEvent(event.getEventId());

		assertThat(renewed).isFalse();

		assertThat(reloadedEvent.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);

		assertThat(reloadedEvent.getLockedUntil()).isBefore(Instant.now());

		assertThat(reloadedEvent.getVersion()).isEqualTo(claimedEvent.getVersion());
	}

	@Test
	void renewLeaseRejectsStaleClaimVersion() {

		OutboxEvent event = savePendingEvent("renew-stale");

		OutboxEvent staleClaim = claimEvent(event.getEventId());

		Long staleVersion = staleClaim.getVersion();

		jdbcTemplate.update("""
				UPDATE outbox_events
				SET version = version + 1
				WHERE event_id = ?
				""", event.getEventId());

		boolean renewed = outboxEventStateService.renewLease(staleClaim, TEST_LEASE_DURATION);

		OutboxEvent reloadedEvent = findEvent(event.getEventId());

		assertThat(renewed).isFalse();

		assertThat(reloadedEvent.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);

		assertThat(reloadedEvent.getVersion()).isEqualTo(staleVersion + 1);
	}

	private OutboxEvent savePendingEvent(String suffix) {

		// Use an old, deterministic eligibility timestamp so test events
		// are not pushed out of the claim batch by unrelated test data.
		return savePendingEvent(suffix, Instant.parse("2000-01-01T00:00:00Z"));
	}

	private OutboxEvent savePendingEvent(String suffix, Instant nextAttemptAt) {

		OutboxEvent event = createEvent(suffix);

		event.setNextAttemptAt(nextAttemptAt);

		return outboxEventRepository.saveAndFlush(event);
	}

	private OutboxEvent saveProcessingEvent(String suffix, Instant lockedUntil, int attempts) {

		OutboxEvent event = createEvent(suffix);

		event.setStatus(OutboxEventStatus.PROCESSING);
		event.setAttempts(attempts);
		event.setLockedUntil(lockedUntil);

		return outboxEventRepository.saveAndFlush(event);
	}

	private OutboxEvent createEvent(String suffix) {

		return new OutboxEvent(TEST_PREFIX + suffix, OutboxEventType.APPOINTMENT_CONFIRMATION, 987654321L, """
				{
				  "recipientEmail": "aarav.sharma@example.com",
				  "patientName": "Aarav Sharma",
				  "doctorName": "Dr. Meera Nair",
				  "appointmentDateTime": "2026-11-10T10:30:00Z",
				  "reason": "Annual health consultation",
				  "appointmentReference": "APPT-987654321",
				  "cancellationReason": null
				}
				""");
	}

	private OutboxEvent claimEvent(String eventId) {

		List<OutboxEvent> claimedEvents = outboxEventClaimService.claimBatch(100, TEST_LEASE_DURATION);

		return claimedEvents.stream().filter(event -> event.getEventId().equals(eventId)).findFirst()
				.orElseThrow(() -> new AssertionError("Expected event to be claimed: " + eventId));
	}

	private OutboxEvent findEvent(String eventId) {

		return outboxEventRepository.findByEventId(eventId)
				.orElseThrow(() -> new AssertionError("Expected outbox event to exist: " + eventId));
	}
}
