package com.medsync.appointment.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.medsync.appointment.entity.OutboxEventStatus;
import com.medsync.appointment.repository.OutboxEventRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class OutboxMetricsTests {

	@Test
	void registersDatabaseBackedGaugeForEveryOutboxStatus() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		OutboxEventRepository repository = mock(OutboxEventRepository.class);

		when(repository.countByStatus(OutboxEventStatus.PENDING)).thenReturn(7L);
		when(repository.countByStatus(OutboxEventStatus.PROCESSING)).thenReturn(2L);
		when(repository.countByStatus(OutboxEventStatus.PUBLISHED)).thenReturn(31L);
		when(repository.countByStatus(OutboxEventStatus.FAILED)).thenReturn(3L);

		new OutboxMetrics(registry, repository);

		assertThat(registry.get("medsync.outbox.events").tag("status", "pending").gauge().value()).isEqualTo(7.0);

		assertThat(registry.get("medsync.outbox.events").tag("status", "processing").gauge().value()).isEqualTo(2.0);

		assertThat(registry.get("medsync.outbox.events").tag("status", "published").gauge().value()).isEqualTo(31.0);

		assertThat(registry.get("medsync.outbox.events").tag("status", "failed").gauge().value()).isEqualTo(3.0);
	}

	@Test
	void recordsOnlySupportedDeliveryOutcomes() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		OutboxMetrics metrics = new OutboxMetrics(registry, mock(OutboxEventRepository.class));

		metrics.recordDelivery("success");
		metrics.recordDelivery("retry");
		metrics.recordDelivery("permanent_failure");

		assertThat(registry.get("medsync.outbox.deliveries").tag("outcome", "success").counter().count())
				.isEqualTo(1.0);

		assertThat(registry.get("medsync.outbox.deliveries").tag("outcome", "retry").counter().count()).isEqualTo(1.0);

		assertThat(registry.get("medsync.outbox.deliveries").tag("outcome", "permanent_failure").counter().count())
				.isEqualTo(1.0);

		assertThatThrownBy(() -> metrics.recordDelivery("event-123")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void recordsDeliveryDurationAndClaimMetrics() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		OutboxMetrics metrics = new OutboxMetrics(registry, mock(OutboxEventRepository.class));

		metrics.recordDeliveryDuration(Duration.ofMillis(125));
		metrics.recordClaimed(4);
		metrics.recordClaimFailure();

		assertThat(registry.get("medsync.outbox.delivery.duration").timer().count()).isEqualTo(1);

		assertThat(registry.get("medsync.outbox.claimed").counter().count()).isEqualTo(4.0);

		assertThat(registry.get("medsync.outbox.claim.failures").counter().count()).isEqualTo(1.0);
	}

	@Test
	void rejectsInvalidDurationAndClaimCount() {
		OutboxMetrics metrics = new OutboxMetrics(new SimpleMeterRegistry(), mock(OutboxEventRepository.class));

		assertThatThrownBy(() -> metrics.recordDeliveryDuration(null)).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> metrics.recordClaimed(-1)).isInstanceOf(IllegalArgumentException.class);
	}
}