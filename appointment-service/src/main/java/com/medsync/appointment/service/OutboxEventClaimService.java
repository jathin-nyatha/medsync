
package com.medsync.appointment.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.properties.OutboxDispatcherProperties;
import com.medsync.appointment.repository.OutboxEventRepository;

@Service
public class OutboxEventClaimService {

	private final OutboxEventRepository outboxEventRepository;
	private final OutboxDispatcherProperties properties;

	public OutboxEventClaimService(OutboxEventRepository outboxEventRepository, OutboxDispatcherProperties properties) {
		this.outboxEventRepository = outboxEventRepository;
		this.properties = properties;
	}

	/**
	 * Retrieves and atomically claims eligible outbox events.
	 *
	 * Expired PROCESSING events that have exhausted their attempts are first
	 * transitioned to FAILED.
	 *
	 * This transaction should remain short. The caller must perform downstream HTTP
	 * calls only after this method has returned.
	 */
	@Transactional
	public List<OutboxEvent> claimBatch(int batchSize, Duration leaseDuration) {

		if (batchSize < 1 || batchSize > 100) {
			throw new IllegalArgumentException("Batch size must be between 1 and 100.");
		}

		if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
			throw new IllegalArgumentException("Lease duration must be positive.");
		}

		long leaseDurationMicros = leaseDuration.toNanos() / 1_000;

		if (leaseDurationMicros < 1) {
			throw new IllegalArgumentException("Lease duration must be at least one microsecond.");
		}

		int maxAttempts = properties.getMaxAttempts();

		if (maxAttempts < 1) {
			throw new IllegalStateException("Maximum outbox attempts must be at least one.");
		}

		// Recover expired events that cannot be retried anymore.
		outboxEventRepository.failExpiredExhaustedEvents(maxAttempts);

		// Candidate selection uses database UTC time.
		List<Long> candidateIds = outboxEventRepository.findEligibleEventIds(maxAttempts, batchSize);

		if (candidateIds.isEmpty()) {
			return List.of();
		}

		List<Long> claimedIds = new ArrayList<>();

		for (Long eventId : candidateIds) {

			// The conditional UPDATE is the authoritative concurrency guard.
			int updatedRows = outboxEventRepository.claimIfEligible(eventId, maxAttempts, leaseDurationMicros);

			if (updatedRows == 1) {
				claimedIds.add(eventId);
			}
		}

		if (claimedIds.isEmpty()) {
			return List.of();
		}

		return outboxEventRepository.findAllById(claimedIds);
	}
}
