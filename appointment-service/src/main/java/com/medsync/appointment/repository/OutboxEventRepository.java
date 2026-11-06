package com.medsync.appointment.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.entity.OutboxEventStatus;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

	List<OutboxEvent> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(OutboxEventStatus status,
			Instant currentTime);

	Optional<OutboxEvent> findByEventId(String eventId);

	boolean existsByEventId(String eventId);

	long countByStatus(OutboxEventStatus status);
}