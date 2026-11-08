
package com.medsync.appointment.worker;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.entity.OutboxEventType;
import com.medsync.appointment.observability.OutboxMetrics;
import com.medsync.appointment.properties.OutboxDispatcherProperties;
import com.medsync.appointment.service.OutboxEventClaimService;
import com.medsync.appointment.service.OutboxEventDeliveryService;
import com.medsync.appointment.service.OutboxEventStateService;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTests {

	@Mock
	private OutboxEventClaimService claimService;

	@Mock
	private OutboxEventDeliveryService deliveryService;

	@Mock
	private OutboxEventStateService stateService;

	@Mock
	private OutboxMetrics outboxMetrics;

	private OutboxDispatcherProperties properties;
	private OutboxDispatcher dispatcher;

	@BeforeEach
	void setUp() {

		properties = new OutboxDispatcherProperties();
		properties.setBatchSize(10);
		properties.setLeaseDuration(Duration.ofSeconds(120));

		dispatcher = new OutboxDispatcher(claimService, deliveryService, stateService, properties, outboxMetrics);
	}

	@Test
	void recordsClaimedCountAndDoesNothingForEmptyBatch() {

		when(claimService.claimBatch(10, Duration.ofSeconds(120))).thenReturn(List.of());

		dispatcher.dispatchPendingEvents();

		verify(outboxMetrics).recordClaimed(0);
		verifyNoInteractions(deliveryService, stateService);
	}

	@Test
	void recordsClaimFailureWhenBatchCannotBeClaimed() {

		when(claimService.claimBatch(10, Duration.ofSeconds(120)))
				.thenThrow(new IllegalStateException("Database unavailable"));

		dispatcher.dispatchPendingEvents();

		verify(outboxMetrics).recordClaimFailure();
		verifyNoInteractions(deliveryService, stateService);
	}

	@Test
	void renewsLeaseBeforeDeliveringEvent() {

		OutboxEvent event = createEvent("event-renew-success");

		when(claimService.claimBatch(10, Duration.ofSeconds(120))).thenReturn(List.of(event));

		when(stateService.renewLease(event, Duration.ofSeconds(120))).thenReturn(true);

		dispatcher.dispatchPendingEvents();

		verify(stateService).renewLease(event, Duration.ofSeconds(120));
		verify(deliveryService).deliver(event);
	}

	@Test
	void skipsDeliveryWhenLeaseRenewalFails() {

		OutboxEvent event = createEvent("event-renew-failed");

		when(claimService.claimBatch(10, Duration.ofSeconds(120))).thenReturn(List.of(event));

		when(stateService.renewLease(event, Duration.ofSeconds(120))).thenReturn(false);

		dispatcher.dispatchPendingEvents();

		verify(stateService).renewLease(event, Duration.ofSeconds(120));
		verify(deliveryService, never()).deliver(event);
	}

	@Test
	void continuesProcessingNextEventWhenLeaseRenewalThrowsException() {

		OutboxEvent firstEvent = createEvent("event-renew-exception");
		OutboxEvent secondEvent = createEvent("event-renew-next");

		when(claimService.claimBatch(10, Duration.ofSeconds(120))).thenReturn(List.of(firstEvent, secondEvent));

		when(stateService.renewLease(firstEvent, Duration.ofSeconds(120)))
				.thenThrow(new IllegalStateException("Database unavailable"));

		when(stateService.renewLease(secondEvent, Duration.ofSeconds(120))).thenReturn(true);

		dispatcher.dispatchPendingEvents();

		verify(deliveryService, never()).deliver(firstEvent);
		verify(deliveryService).deliver(secondEvent);
	}

	private OutboxEvent createEvent(String eventId) {

		OutboxEvent event = new OutboxEvent(eventId, OutboxEventType.APPOINTMENT_CONFIRMATION, 987654321L, "{}");

		event.setId(1001L);
		event.setVersion(1L);

		return event;
	}
}
