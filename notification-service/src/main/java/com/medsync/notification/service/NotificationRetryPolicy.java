
package com.medsync.notification.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationRetryPolicy {

	private final int maxAttempts;
	private final Duration baseDelay;
	private final Duration maxDelay;

	public NotificationRetryPolicy(@Value("${medsync.notification.delivery.max-attempts:5}") int maxAttempts,
			@Value("${medsync.notification.delivery.base-delay-seconds:30}") long baseDelaySeconds,
			@Value("${medsync.notification.delivery.max-delay-seconds:21600}") long maxDelaySeconds) {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("Maximum notification attempts must be at least 1.");
		}

		if (baseDelaySeconds < 1 || maxDelaySeconds < baseDelaySeconds) {
			throw new IllegalArgumentException("Invalid notification retry delay configuration.");
		}

		this.maxAttempts = maxAttempts;
		this.baseDelay = Duration.ofSeconds(baseDelaySeconds);
		this.maxDelay = Duration.ofSeconds(maxDelaySeconds);
	}

	public boolean canRetry(int currentAttempt) {
		return currentAttempt < maxAttempts;
	}

	public Instant calculateNextAttempt(int currentAttempt, Instant now) {
		if (currentAttempt < 1) {
			throw new IllegalArgumentException("Attempt number must be at least 1.");
		}

		int exponent = Math.min(currentAttempt - 1, 30);

		long exponentialSeconds;

		try {
			exponentialSeconds = Math.multiplyExact(baseDelay.toSeconds(), 1L << exponent);
		} catch (ArithmeticException exception) {
			exponentialSeconds = maxDelay.toSeconds();
		}

		long cappedSeconds = Math.min(exponentialSeconds, maxDelay.toSeconds());

		// Apply jitter between 50% and 100% of the capped delay.
		long minimumSeconds = Math.max(1, cappedSeconds / 2);

		long delaySeconds = ThreadLocalRandom.current().nextLong(minimumSeconds, cappedSeconds + 1);

		return now.plusSeconds(delaySeconds);
	}
}
