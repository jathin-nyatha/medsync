package com.medsync.appointment.worker;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.observability.OutboxMetrics;
import com.medsync.appointment.properties.OutboxDispatcherProperties;
import com.medsync.appointment.service.OutboxEventClaimService;
import com.medsync.appointment.service.OutboxEventDeliveryService;

@Component
@ConditionalOnProperty(prefix = "medsync.outbox.dispatcher", name = "enabled", havingValue = "true")
public class OutboxDispatcher {

	private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

	private final OutboxEventClaimService claimService;
	private final OutboxEventDeliveryService deliveryService;
	private final OutboxDispatcherProperties properties;
	private final OutboxMetrics outboxMetrics;

	public OutboxDispatcher(OutboxEventClaimService claimService, OutboxEventDeliveryService deliveryService,
			OutboxDispatcherProperties properties, OutboxMetrics outboxMetrics) {
		this.claimService = claimService;
		this.deliveryService = deliveryService;
		this.properties = properties;
		this.outboxMetrics = outboxMetrics;
		this.properties.validate();
	}

	@Scheduled(fixedDelayString = "${medsync.outbox.dispatcher.fixed-delay:5s}")
	public void dispatchPendingEvents() {
		List<OutboxEvent> claimedEvents;
		try {
			claimedEvents = claimService.claimBatch(properties.getBatchSize(), properties.getLeaseDuration());
		} catch (RuntimeException exception) {
			outboxMetrics.recordClaimFailure();
			log.error("Unable to claim outbox events. Dispatcher will retry during the next polling cycle.", exception);
			return;
		}

		outboxMetrics.recordClaimed(claimedEvents.size());

		if (claimedEvents.isEmpty()) {
			return;
		}

		log.info("Claimed {} outbox event(s) for delivery.", claimedEvents.size());
		for (OutboxEvent event : claimedEvents) {
			try {
				deliveryService.deliver(event);
			} catch (RuntimeException exception) {
				log.error("Unexpected processing failure for outbox event {}. Its lease will allow recovery.",
						event.getEventId(), exception);
			}
		}
	}
}