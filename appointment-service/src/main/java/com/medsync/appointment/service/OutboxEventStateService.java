package com.medsync.appointment.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpStatusCodeException;

import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.observability.OutboxMetrics;
import com.medsync.appointment.properties.OutboxDispatcherProperties;
import com.medsync.appointment.repository.OutboxEventRepository;

@Service
public class OutboxEventStateService {

	private static final Logger log = LoggerFactory.getLogger(OutboxEventStateService.class);
	private static final int MAX_ERROR_LENGTH = 2000;

	private final OutboxEventRepository outboxEventRepository;
	private final OutboxMetrics outboxMetrics;

	public OutboxEventStateService(OutboxEventRepository outboxEventRepository, OutboxMetrics outboxMetrics) {
		this.outboxEventRepository = outboxEventRepository;
		this.outboxMetrics = outboxMetrics;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean markPublished(OutboxEvent claimedEvent) {
		validateClaimedEvent(claimedEvent);

		int updatedRows = outboxEventRepository.markPublishedIfClaimed(claimedEvent.getId(), claimedEvent.getVersion(),
				Instant.now());

		if (updatedRows == 0) {
			log.warn("Could not mark outbox event {} as PUBLISHED because its claim is no longer current.",
					claimedEvent.getEventId());
			return false;
		}

		recordMetricAfterCommit("success");
		return true;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean recordFailure(OutboxEvent claimedEvent, Throwable failure, boolean retryable,
			OutboxDispatcherProperties properties) {
		validateClaimedEvent(claimedEvent);

		if (failure == null) {
			throw new IllegalArgumentException("Failure details are required.");
		}

		String error = describeFailure(failure);
		boolean attemptsExhausted = claimedEvent.getAttempts() >= properties.getMaxAttempts();

		if (!retryable || attemptsExhausted) {
			int updatedRows = outboxEventRepository.markFailedIfClaimed(claimedEvent.getId(), claimedEvent.getVersion(),
					error);
			if (updatedRows == 0) {
				log.warn("Could not mark outbox event {} as FAILED because its claim is no longer current.",
						claimedEvent.getEventId());
				return false;
			}

			recordMetricAfterCommit("permanent_failure");
			log.error("Outbox event {} permanently failed after {} attempt(s). Reason: {}", claimedEvent.getEventId(),
					claimedEvent.getAttempts(), error);
			return true;
		}

		Duration retryDelay = calculateRetryDelay(claimedEvent.getAttempts(), properties.getInitialRetryDelay(),
				properties.getMaxRetryDelay());
		Instant nextAttemptAt = Instant.now().plus(retryDelay);

		int updatedRows = outboxEventRepository.rescheduleIfClaimed(claimedEvent.getId(), claimedEvent.getVersion(),
				nextAttemptAt, error);
		if (updatedRows == 0) {
			log.warn("Could not reschedule outbox event {} because its claim is no longer current.",
					claimedEvent.getEventId());
			return false;
		}

		recordMetricAfterCommit("retry");
		log.warn("Outbox event {} scheduled for retry at {}. Attempt: {}/{}. Reason: {}", claimedEvent.getEventId(),
				nextAttemptAt, claimedEvent.getAttempts(), properties.getMaxAttempts(), error);
		return true;
	}

	private void recordMetricAfterCommit(String outcome) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			outboxMetrics.recordDelivery(outcome);
			return;
		}

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				outboxMetrics.recordDelivery(outcome);
			}
		});
	}

	private Duration calculateRetryDelay(int attempts, Duration initialDelay, Duration maximumDelay) {
		long delayMillis = initialDelay.toMillis();
		long maximumMillis = maximumDelay.toMillis();

		for (int i = 1; i < attempts && delayMillis < maximumMillis; i++) {
			if (delayMillis > maximumMillis / 2) {
				delayMillis = maximumMillis;
				break;
			}
			delayMillis *= 2;
		}
		return Duration.ofMillis(Math.min(delayMillis, maximumMillis));
	}

	private void validateClaimedEvent(OutboxEvent event) {
		if (event == null || event.getId() == null || event.getVersion() == null) {
			throw new IllegalArgumentException("A persisted claimed outbox event with a version is required.");
		}
	}

	private String describeFailure(Throwable failure) {
		if (failure instanceof HttpStatusCodeException httpException) {
			return truncate("Notification Service returned HTTP " + httpException.getStatusCode().value());
		}

		String message = failure.getMessage();
		if (message == null || message.isBlank()) {
			message = failure.getClass().getSimpleName();
		} else {
			message = failure.getClass().getSimpleName() + ": " + message;
		}
		return truncate(message);
	}

	private String truncate(String value) {
		return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
	}
}