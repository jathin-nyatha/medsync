
package com.medsync.notification.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medsync.notification.entity.DeliveryAttemptStatus;
import com.medsync.notification.entity.Notification;
import com.medsync.notification.entity.NotificationAttempt;
import com.medsync.notification.repository.NotificationAttemptRepository;
import com.medsync.notification.repository.NotificationRepository;

@Service
public class NotificationDeliveryResultService {

	private final NotificationRepository notificationRepository;
	private final NotificationAttemptRepository attemptRepository;

	public NotificationDeliveryResultService(NotificationRepository notificationRepository,
			NotificationAttemptRepository attemptRepository) {
		this.notificationRepository = notificationRepository;
		this.attemptRepository = attemptRepository;
	}

	@Transactional
	public void recordAccepted(Long notificationId, int attemptNumber, int providerStatusCode,
			String providerMessageId) {
		Notification notification = getNotification(notificationId);

		validateAttemptNumber(notification, attemptNumber);

		NotificationAttempt attempt = new NotificationAttempt(notification, attemptNumber,
				DeliveryAttemptStatus.SUCCESS, providerStatusCode, providerMessageId, null);

		attemptRepository.save(attempt);
		notification.markAccepted(providerMessageId);
	}

	@Transactional
	public void recordFailure(Long notificationId, int attemptNumber, Integer providerStatusCode, String errorMessage,
			Instant retryAt) {
		Notification notification = getNotification(notificationId);

		validateAttemptNumber(notification, attemptNumber);

		NotificationAttempt attempt = new NotificationAttempt(notification, attemptNumber, DeliveryAttemptStatus.FAILED,
				providerStatusCode, null, errorMessage);

		attemptRepository.save(attempt);
		notification.markFailed(errorMessage, retryAt);
	}

	private Notification getNotification(Long notificationId) {
		return notificationRepository.findById(notificationId)
				.orElseThrow(() -> new IllegalStateException("Notification not found: " + notificationId));
	}

	private void validateAttemptNumber(Notification notification, int attemptNumber) {
		if (notification.getAttemptCount() != attemptNumber) {
			throw new IllegalStateException("Notification attempt count changed before recording the result.");
		}
	}
}
