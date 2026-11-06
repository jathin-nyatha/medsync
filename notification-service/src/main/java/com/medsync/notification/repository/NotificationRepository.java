package com.medsync.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.medsync.notification.entity.Notification;
import com.medsync.notification.entity.NotificationStatus;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

	Optional<Notification> findByIdempotencyKey(String idempotencyKey);

	Optional<Notification> findByProviderMessageId(String providerMessageId);

	Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

	/**
	 * Finds notifications eligible for delivery:
	 *
	 * 1. New PENDING notifications. 2. FAILED notifications whose retry time has
	 * arrived. 3. PROCESSING notifications whose processing lease has expired.
	 *
	 * This query identifies candidates only. It does not claim them.
	 */
	@Query(value = """
			SELECT id
			FROM notifications
			WHERE
			    (status = 'PENDING' AND next_attempt_at IS NULL)
			    OR
			    (status = 'FAILED'
			        AND next_attempt_at IS NOT NULL
			        AND next_attempt_at <= :currentTime)
			    OR
			    (status = 'PROCESSING'
			        AND locked_until IS NOT NULL
			        AND locked_until <= :currentTime)
			ORDER BY COALESCE(next_attempt_at, locked_until, created_at), id
			LIMIT :batchSize
			""", nativeQuery = true)
	List<Long> findEligibleNotificationIds(@Param("currentTime") Instant currentTime,
			@Param("batchSize") int batchSize);

	/**
	 * Atomically claims a notification.
	 *
	 * The conditional WHERE clause prevents multiple workers from claiming the same
	 * notification while its lease is active.
	 *
	 * An expired PROCESSING notification can be reclaimed.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE notifications
			SET
			    status = 'PROCESSING',
			    attempt_count = attempt_count + 1,
			    next_attempt_at = NULL,
			    locked_until = :leaseUntil,
			    last_error = NULL,
			    updated_at = UTC_TIMESTAMP(6),
			    version = version + 1
			WHERE
			    id = :notificationId
			    AND
			    (
			        (status = 'PENDING' AND next_attempt_at IS NULL)
			        OR
			        (status = 'FAILED'
			            AND next_attempt_at IS NOT NULL
			            AND next_attempt_at <= :currentTime)
			        OR
			        (status = 'PROCESSING'
			            AND locked_until IS NOT NULL
			            AND locked_until <= :currentTime)
			    )
			""", nativeQuery = true)
	int claimIfEligible(@Param("notificationId") Long notificationId, @Param("currentTime") Instant currentTime,
			@Param("leaseUntil") Instant leaseUntil);
}