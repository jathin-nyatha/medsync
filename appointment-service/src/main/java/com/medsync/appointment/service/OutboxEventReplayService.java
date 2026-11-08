
package com.medsync.appointment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medsync.appointment.repository.OutboxEventRepository;

@Service
public class OutboxEventReplayService {

	private final OutboxEventRepository outboxEventRepository;

	public OutboxEventReplayService(OutboxEventRepository outboxEventRepository) {
		this.outboxEventRepository = outboxEventRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ReplayResult replay(String eventId) {

		if (eventId == null || eventId.isBlank()) {
			throw new IllegalArgumentException("Outbox event ID is required.");
		}

		int updatedRows = outboxEventRepository.replayFailedEvent(eventId);

		if (updatedRows == 1) {
			return ReplayResult.REPLAYED;
		}

		if (!outboxEventRepository.existsByEventId(eventId)) {
			return ReplayResult.NOT_FOUND;
		}

		return ReplayResult.NOT_FAILED;
	}

	public enum ReplayResult {
		REPLAYED, NOT_FOUND, NOT_FAILED
	}
}
