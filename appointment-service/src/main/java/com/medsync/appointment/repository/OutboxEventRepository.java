
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
	 * Finds candidate events. This query does not claim them.
	 *
	 * Eligible events: 1. PENDING events whose retry time has arrived. 2.
	 * PROCESSING events whose lease has expired.
	 */
	@Query(value = """
			SELECT id
			FROM outbox_events
			WHERE
			    (
			        status = 'PENDING'
			        AND next_attempt_at <= :currentTime
			    )
			    OR
			    (
			        status = 'PROCESSING'
			        AND locked_until IS NOT NULL
			        AND locked_until <= :currentTime
			    )
			ORDER BY COALESCE(next_attempt_at, locked_until, created_at), id
			LIMIT :batchSize
			""", nativeQuery = true)
	List<Long> findEligibleEventIds(@Param("currentTime") Instant currentTime, @Param("batchSize") int batchSize);

	/**
	 * Atomically claims an eligible event. Attempts increments once per successful
	 * claim.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE outbox_events
			SET
			    status = 'PROCESSING',
			    attempts = attempts + 1,
			    locked_until = :leaseUntil,
			    last_error = NULL,
			    updated_at = UTC_TIMESTAMP(6),
			    version = version + 1
			WHERE
			    id = :eventId
			    AND
			    (
			        (
			            status = 'PENDING'
			            AND next_attempt_at <= :currentTime
			        )
			        OR
			        (
			            status = 'PROCESSING'
			            AND locked_until IS NOT NULL
			            AND locked_until <= :currentTime
			        )
			    )
			""", nativeQuery = true)
	int claimIfEligible(@Param("eventId") Long eventId, @Param("currentTime") Instant currentTime,
			@Param("leaseUntil") Instant leaseUntil);

	/**
	 * Renews an active lease only if the worker still owns the expected version. An
	 * expired lease cannot be renewed.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE outbox_events
			SET
			    locked_until = :leaseUntil,
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
			@Param("leaseUntil") Instant leaseUntil);

	/**
	 * Marks an event as published only if its claim is still current.
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
	 * Reschedules an event only if its claim is still current.
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
	 * Marks an event as permanently failed only if its claim is current.
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
	 * Requeues a permanently failed event for an explicit administrative replay.
	 *
	 * The event identity and payload are deliberately preserved. The status
	 * predicate makes this operation safe against concurrent replay requests and
	 * prevents replay of PENDING, PROCESSING, or PUBLISHED events.
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
