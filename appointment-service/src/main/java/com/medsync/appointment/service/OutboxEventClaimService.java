package com.medsync.appointment.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.repository.OutboxEventRepository;

@Service
public class OutboxEventClaimService {

	private final OutboxEventRepository outboxEventRepository;

	public OutboxEventClaimService(OutboxEventRepository outboxEventRepository) {
		this.outboxEventRepository = outboxEventRepository;
	}

	/**
	 * Retrieves and atomically claims eligible outbox events.
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

		Instant currentTime = Instant.now();
		Instant leaseUntil = currentTime.plus(leaseDuration);

		List<Long> candidateIds = outboxEventRepository.findEligibleEventIds(currentTime, batchSize);

		if (candidateIds.isEmpty()) {
			return List.of();
		}

		List<Long> claimedIds = new ArrayList<>();

		for (Long eventId : candidateIds) {

			int updatedRows = outboxEventRepository.claimIfEligible(eventId, currentTime, leaseUntil);

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