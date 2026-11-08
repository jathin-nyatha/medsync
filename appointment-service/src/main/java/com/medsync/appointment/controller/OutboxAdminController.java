
package com.medsync.appointment.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medsync.appointment.service.OutboxEventReplayService;
import com.medsync.appointment.service.OutboxEventReplayService.ReplayResult;

@RestController
@RequestMapping("/api/internal/outbox/events")
public class OutboxAdminController {

	private final OutboxEventReplayService outboxEventReplayService;

	public OutboxAdminController(OutboxEventReplayService outboxEventReplayService) {
		this.outboxEventReplayService = outboxEventReplayService;
	}

	/**
	 * Requeues one FAILED outbox event. Only ADMIN users may perform this
	 * operation.
	 */
	@PostMapping("/{eventId}/replay")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Map<String, String>> replayFailedEvent(@PathVariable String eventId) {

		ReplayResult result = outboxEventReplayService.replay(eventId);

		return switch (result) {
		case REPLAYED -> ResponseEntity.accepted()
				.body(Map.of("eventId", eventId, "status", "PENDING", "message", "Outbox event queued for replay."));

		case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of("eventId", eventId, "message", "Outbox event was not found."));

		case NOT_FAILED -> ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("eventId", eventId, "message", "Only FAILED outbox events can be replayed."));
		};
	}
}
