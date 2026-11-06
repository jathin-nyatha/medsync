package com.medsync.notification.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.medsync.notification.dto.EmailMessage;
import com.medsync.notification.entity.Notification;
import com.medsync.notification.repository.NotificationRepository;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkServiceException;

@Component
@ConditionalOnProperty(prefix = "medsync.notification.delivery", name = "enabled", havingValue = "true")
public class NotificationDeliveryWorker {

	private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryWorker.class);

	private final NotificationRepository notificationRepository;
	private final NotificationClaimService claimService;
	private final NotificationDeliveryResultService resultService;
	private final NotificationRetryPolicy retryPolicy;
	private final EmailProvider emailProvider;
	private final int batchSize;

	public NotificationDeliveryWorker(NotificationRepository notificationRepository,
			NotificationClaimService claimService, NotificationDeliveryResultService resultService,
			NotificationRetryPolicy retryPolicy, EmailProvider emailProvider,
			@Value("${medsync.notification.delivery.batch-size:25}") int batchSize) {

		if (batchSize < 1) {
			throw new IllegalArgumentException("Notification delivery batch size must be at least 1.");
		}

		this.notificationRepository = notificationRepository;
		this.claimService = claimService;
		this.resultService = resultService;
		this.retryPolicy = retryPolicy;
		this.emailProvider = emailProvider;
		this.batchSize = batchSize;
	}

	@Scheduled(fixedDelayString = "${medsync.notification.delivery.poll-interval-ms:5000}")
	public void processPendingNotifications() {

		Instant now = Instant.now();

		List<Long> notificationIds = notificationRepository.findEligibleNotificationIds(now, batchSize);

		for (Long notificationId : notificationIds) {
			try {
				processNotification(notificationId, now);
			} catch (Exception exception) {
				log.error("Unexpected error processing notification ID {}. "
						+ "It may be recovered after its processing lease expires.", notificationId, exception);
			}
		}
	}

	private void processNotification(Long notificationId, Instant now) {

		Optional<Notification> claimed = claimService.claim(notificationId, now);

		if (claimed.isEmpty()) {
			log.debug("Notification ID {} was already claimed or is no longer eligible.", notificationId);
			return;
		}

		Notification notification = claimed.get();
		int attemptNumber = notification.getAttemptCount();

		EmailMessage emailMessage = new EmailMessage(notification.getRecipientEmail(), notification.getSubject(),
				notification.getPlainTextContent(), notification.getHtmlContent());

		EmailProvider.EmailDeliveryResult deliveryResult;

		// Only exceptions from the provider call are delivery failures.
		try {
			deliveryResult = emailProvider.send(emailMessage);
		} catch (RuntimeException exception) {
			handleDeliveryFailure(notification, attemptNumber, exception);
			return;
		}

		// Persisting provider acceptance is a separate operation.
		// If this fails, the lease will eventually expire and allow recovery.
		resultService.recordAccepted(notification.getId(), attemptNumber, deliveryResult.statusCode(),
				deliveryResult.providerMessageId());

		log.info("Notification ID {} accepted by email provider on attempt {}.", notificationId, attemptNumber);
	}

	private void handleDeliveryFailure(Notification notification, int attemptNumber, RuntimeException exception) {

		boolean retryable = isRetryable(exception);
		Instant retryAt = null;

		if (retryable && retryPolicy.canRetry(attemptNumber)) {
			retryAt = retryPolicy.calculateNextAttempt(attemptNumber, Instant.now());
		}

		Integer providerStatusCode = findProviderStatusCode(exception);
		String errorMessage = safeErrorMessage(exception);

		resultService.recordFailure(notification.getId(), attemptNumber, providerStatusCode, errorMessage, retryAt);

		if (retryAt != null) {
			log.warn("Notification ID {} failed on attempt {}. Retry scheduled at {}.", notification.getId(),
					attemptNumber, retryAt);
		} else {
			log.error("Notification ID {} failed permanently on attempt {}.", notification.getId(), attemptNumber);
		}
	}

	private boolean isRetryable(Throwable exception) {

		Throwable current = exception;

		while (current != null) {

			if (current instanceof SdkClientException) {
				return true;
			}

			if (current instanceof SdkServiceException serviceException) {
				int statusCode = serviceException.statusCode();
				return statusCode == 429 || statusCode >= 500;
			}

			current = current.getCause();
		}

		return false;
	}

	private Integer findProviderStatusCode(Throwable exception) {

		Throwable current = exception;

		while (current != null) {

			if (current instanceof SdkServiceException serviceException) {
				return serviceException.statusCode();
			}

			current = current.getCause();
		}

		return null;
	}

	private String safeErrorMessage(Throwable exception) {

		String message = exception.getMessage();

		if (message == null || message.isBlank()) {
			message = exception.getClass().getSimpleName();
		}

		return message.length() > 1000 ? message.substring(0, 1000) : message;
	}
}