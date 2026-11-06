package com.medsync.appointment.observability;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.medsync.appointment.entity.OutboxEventStatus;
import com.medsync.appointment.repository.OutboxEventRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Registers bounded-cardinality metrics for the transactional outbox. Status
 * gauges are read from the database so they remain meaningful across multiple
 * Appointment Service instances.
 */
@Component
public class OutboxMetrics {

	private static final String DELIVERY_COUNTER = "medsync.outbox.deliveries";
	private static final String DELIVERY_TIMER = "medsync.outbox.delivery.duration";

	private final MeterRegistry meterRegistry;

	public OutboxMetrics(MeterRegistry meterRegistry, OutboxEventRepository repository) {
		this.meterRegistry = meterRegistry;

		for (OutboxEventStatus status : OutboxEventStatus.values()) {
			Gauge.builder("medsync.outbox.events", repository, source -> source.countByStatus(status))
					.tag("status", status.name().toLowerCase())
					.description("Current number of outbox events by persisted status").register(meterRegistry);
		}
	}

	public void recordDelivery(String outcome) {
		if (!"success".equals(outcome) && !"retry".equals(outcome) && !"permanent_failure".equals(outcome)) {
			throw new IllegalArgumentException("Unsupported outbox delivery outcome.");
		}

		Counter.builder(DELIVERY_COUNTER).tag("outcome", outcome).description("Outbox delivery state transitions")
				.register(meterRegistry).increment();
	}

	public void recordDeliveryDuration(Duration duration) {
		if (duration == null || duration.isNegative()) {
			throw new IllegalArgumentException("A non-negative delivery duration is required.");
		}

		Timer.builder(DELIVERY_TIMER).description("Time spent attempting one outbox event delivery")
				.register(meterRegistry).record(duration);
	}

	public void recordClaimed(int count) {
		if (count < 0) {
			throw new IllegalArgumentException("Claimed event count cannot be negative.");
		}
		if (count > 0) {
			Counter.builder("medsync.outbox.claimed").description("Total outbox events successfully claimed")
					.register(meterRegistry).increment(count);
		}
	}

	public void recordClaimFailure() {
		Counter.builder("medsync.outbox.claim.failures").description("Outbox batch-claim operation failures")
				.register(meterRegistry).increment();
	}
}