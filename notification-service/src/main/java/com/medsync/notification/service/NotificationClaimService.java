package com.medsync.notification.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medsync.notification.entity.Notification;
import com.medsync.notification.repository.NotificationRepository;

@Service
public class NotificationClaimService {

	private final NotificationRepository notificationRepository;
	private final long leaseDurationSeconds;

	public NotificationClaimService(NotificationRepository notificationRepository,
			@Value("${medsync.notification.delivery.lease-duration-seconds:300}") long leaseDurationSeconds) {

		if (leaseDurationSeconds < 1) {
			throw new IllegalArgumentException("Notification processing lease duration must be at least 1 second.");
		}

		this.notificationRepository = notificationRepository;
		this.leaseDurationSeconds = leaseDurationSeconds;
	}

	/**
	 * Attempts to claim one eligible notification.
	 *
	 * A successful claim sets a processing lease. If a worker crashes, another
	 * worker can reclaim the notification after the lease expires.
	 *
	 * @return the claimed notification, or empty if it is not eligible
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<Notification> claim(Long notificationId, Instant currentTime) {

		Instant leaseUntil = currentTime.plusSeconds(leaseDurationSeconds);

		int updatedRows = notificationRepository.claimIfEligible(notificationId, currentTime, leaseUntil);

		if (updatedRows == 0) {
			return Optional.empty();
		}

		return Optional.of(notificationRepository.findById(notificationId)
				.orElseThrow(() -> new IllegalStateException("Claimed notification was not found: " + notificationId)));
	}
}