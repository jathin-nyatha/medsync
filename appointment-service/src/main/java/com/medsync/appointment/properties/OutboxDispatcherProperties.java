package com.medsync.appointment.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "medsync.outbox.dispatcher")
public class OutboxDispatcherProperties {

	private boolean enabled = false;

	private Duration fixedDelay = Duration.ofSeconds(5);

	private int batchSize = 10;

	private Duration leaseDuration = Duration.ofSeconds(120);

	private int maxAttempts = 5;

	private Duration initialRetryDelay = Duration.ofSeconds(5);

	private Duration maxRetryDelay = Duration.ofSeconds(300);

	public void validate() {

		if (fixedDelay == null || fixedDelay.isNegative() || fixedDelay.isZero()) {
			throw new IllegalStateException("Outbox dispatcher fixed delay must be positive.");
		}

		if (batchSize < 1 || batchSize > 100) {
			throw new IllegalStateException("Outbox dispatcher batch size must be between 1 and 100.");
		}

		if (leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()) {
			throw new IllegalStateException("Outbox dispatcher lease duration must be positive.");
		}

		if (maxAttempts < 1) {
			throw new IllegalStateException("Outbox dispatcher maximum attempts must be at least 1.");
		}

		if (initialRetryDelay == null || initialRetryDelay.isNegative() || initialRetryDelay.isZero()
				|| initialRetryDelay.toMillis() < 1) {
			throw new IllegalStateException("Initial retry delay must be at least one millisecond.");
		}

		if (maxRetryDelay == null || maxRetryDelay.compareTo(initialRetryDelay) < 0) {
			throw new IllegalStateException("Maximum retry delay cannot be less than initial retry delay.");
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Duration getFixedDelay() {
		return fixedDelay;
	}

	public void setFixedDelay(Duration fixedDelay) {
		this.fixedDelay = fixedDelay;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	public Duration getLeaseDuration() {
		return leaseDuration;
	}

	public void setLeaseDuration(Duration leaseDuration) {
		this.leaseDuration = leaseDuration;
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}

	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	public Duration getInitialRetryDelay() {
		return initialRetryDelay;
	}

	public void setInitialRetryDelay(Duration initialRetryDelay) {
		this.initialRetryDelay = initialRetryDelay;
	}

	public Duration getMaxRetryDelay() {
		return maxRetryDelay;
	}

	public void setMaxRetryDelay(Duration maxRetryDelay) {
		this.maxRetryDelay = maxRetryDelay;
	}
}