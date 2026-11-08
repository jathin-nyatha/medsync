
package com.medsync.appointment.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.entity.OutboxEventStatus;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

	/**
	 * Moves expired PROCESSING events to FAILED when they have exhausted the
	 * configured number of delivery attempts.
	 *
	 * This operation is atomic and safe to execute from multiple workers.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE outbox_events
			SET
			    status = 'FAILED',
			    locked_until = NULL,
			    last_error = 'Lease expired after maximum delivery attempts',
			    processed_at = NULL,
			    updated_at = UTC_TIMESTAMP(6),
			    version = version + 1
			WHERE
			    status = 'PROCESSING'
			    AND locked_until IS NOT NULL
			    AND locked_until <= UTC_TIMESTAMP(6)
			    AND attempts >= :maxAttempts
			""", nativeQuery = true)
	int failExpiredExhaustedEvents(@Param("maxAttempts") int maxAttempts);

	/**
	 * Finds eligible events without claiming them.
	 *
	 * Eligible events: 1. PENDING events whose retry time has arrived and which
	 * have attempts remaining. 2. PROCESSING events whose lease has expired and
	 * which have attempts remaining.
	 *
	 * Database UTC time is authoritative for eligibility.
	 */
	@Query(value = """
			SELECT id
			FROM outbox_events
			WHERE
			    (
			        status = 'PENDING'
			        AND next_attempt_at <= UTC_TIMESTAMP(6)
			        AND attempts < :maxAttempts
			    )
			    OR
			    (
			        status = 'PROCESSING'
			        AND locked_until IS NOT NULL
			        AND locked_until <= UTC_TIMESTAMP(6)
			        AND attempts < :maxAttempts
			    )
			ORDER BY COALESCE(next_attempt_at, locked_until, created_at), id
			LIMIT :batchSize
			""", nativeQuery = true)
	List<Long> findEligibleEventIds(@Param("maxAttempts") int maxAttempts, @Param("batchSize") int batchSize);

	/**
	 * Atomically claims an eligible event.
	 *
	 * Attempts increments exactly once per successful claim. The attempt limit is
	 * checked again here to protect against concurrent workers that selected the
	 * same candidate.
	 *
	 * Lease expiration and creation use database UTC time.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE outbox_events
			SET
			    status = 'PROCESSING',
			    attempts = attempts + 1,
			    locked_until = DATE_ADD(
			        UTC_TIMESTAMP(6),
			        INTERVAL :leaseDurationMicros MICROSECOND
			    ),
			    last_error = NULL,
			    updated_at = UTC_TIMESTAMP(6),
			    version = version + 1
			WHERE
			    id = :eventId
			    AND attempts < :maxAttempts
			    AND
			    (
			        (
			            status = 'PENDING'
			            AND next_attempt_at <= UTC_TIMESTAMP(6)
			        )
			        OR
			        (
			            status = 'PROCESSING'
			            AND locked_until IS NOT NULL
			            AND locked_until <= UTC_TIMESTAMP(6)
			        )
			    )
			""", nativeQuery = true)
	int claimIfEligible(@Param("eventId") Long eventId, @Param("maxAttempts") int maxAttempts,
			@Param("leaseDurationMicros") long leaseDurationMicros);

	/**
	 * Renews an active lease only if the worker still owns the expected version.
	 *
	 * An expired lease cannot be renewed. This prevents a worker from resuming
	 * delivery after another worker becomes eligible to reclaim it.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE outbox_events
			SET
			    locked_until = DATE_ADD(
			        UTC_TIMESTAMP(6),
			        INTERVAL :leaseDurationMicros MICROSECOND
			    ),
			    updated_at = UTC_TIMESTAMP(6),
			    version = version + 1
			WHERE
			    id = :eventId
			    AND status = 'PROCESSING'
			    AND version = :expectedVersion
			    AND locked_until IS NOT NULL
			    AND locked_until > UTC_TIMESTAMP(6)
			""", nativeQuery = true)
	int renewLeaseIfClaimed(@Param("eventId") Long eventId, @Param("expectedVersion") Long expectedVersion,
			@Param("leaseDurationMicros") long leaseDurationMicros);

	/**
	 * Retrieves the database-calculated lease expiration after renewal.
	 */
	@Query("SELECT event.lockedUntil FROM OutboxEvent event WHERE event.id = :eventId")
	Instant findLockedUntilById(@Param("eventId") Long eventId);

	/**
	 * Marks an event as published only if it is still owned by the worker that
	 * claimed the expected version.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE outbox_events
			SET
			    status = 'PUBLISHED',
			    processed_at = :processedAt,
			    locked_until = NULL,
			    last_error = NULL,
			    updated_at = UTC_TIMESTAMP(6),
			    version = version + 1
			WHERE
			    id = :eventId
			    AND status = 'PROCESSING'
			    AND version = :expectedVersion
			""", nativeQuery = true)
	int markPublishedIfClaimed(@Param("eventId") Long eventId, @Param("expectedVersion") Long expectedVersion,
			@Param("processedAt") Instant processedAt);

	/**
	 * Reschedules an event for retry only if the claim is still current.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE outbox_events
			SET
			    status = 'PENDING',
			    next_attempt_at = :nextAttemptAt,
			    locked_until = NULL,
			    last_error = :lastError,
			    processed_at = NULL,
			    updated_at = UTC_TIMESTAMP(6),
			    version = version + 1
			WHERE
			    id = :eventId
			    AND status = 'PROCESSING'
			    AND version = :expectedVersion
			""", nativeQuery = true)
	int rescheduleIfClaimed(@Param("eventId") Long eventId, @Param("expectedVersion") Long expectedVersion,
			@Param("nextAttemptAt") Instant nextAttemptAt, @Param("lastError") String lastError);

	/**
	 * Marks an event as permanently failed only if the claim is current.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE outbox_events
			SET
			    status = 'FAILED',
			    locked_until = NULL,
			    last_error = :lastError,
			    processed_at = NULL,
			    updated_at = UTC_TIMESTAMP(6),
			    version = version + 1
			WHERE
			    id = :eventId
			    AND status = 'PROCESSING'
			    AND version = :expectedVersion
			""", nativeQuery = true)
	int markFailedIfClaimed(@Param("eventId") Long eventId, @Param("expectedVersion") Long expectedVersion,
			@Param("lastError") String lastError);

	/**
	 * Atomically requeues a FAILED event for administrative replay.
	 *
	 * Only one concurrent request can transition the event because the update
	 * requires the current status to be FAILED.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE outbox_events
			SET
			    status = 'PENDING',
			    attempts = 0,
			    next_attempt_at = UTC_TIMESTAMP(6),
			    locked_until = NULL,
			    last_error = NULL,
			    processed_at = NULL,
			    updated_at = UTC_TIMESTAMP(6),
			    version = version + 1
			WHERE
			    event_id = :eventId
			    AND status = 'FAILED'
			""", nativeQuery = true)
	int replayFailedEvent(@Param("eventId") String eventId);

	Optional<OutboxEvent> findByEventId(String eventId);

	boolean existsByEventId(String eventId);

	long countByStatus(OutboxEventStatus status);
}
