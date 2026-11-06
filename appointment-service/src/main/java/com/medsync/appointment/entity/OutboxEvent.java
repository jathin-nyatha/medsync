package com.medsync.appointment.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "outbox_events", indexes = {
		@Index(name = "idx_outbox_status_next_attempt", columnList = "status,next_attempt_at"),
		@Index(name = "idx_outbox_created_at", columnList = "created_at") })
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, unique = true, length = 100)
	private String eventId;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 50)
	private OutboxEventType eventType;

	@Column(name = "appointment_id", nullable = false)
	private Long appointmentId;

	@Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private OutboxEventStatus status;

	@Column(name = "attempts", nullable = false)
	private int attempts;

	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	@Column(name = "last_error", length = 2000)
	private String lastError;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	@Version
	@Column(name = "version")
	private Long version;

	public OutboxEvent(String eventId, OutboxEventType eventType, Long appointmentId, String payload) {

		this.eventId = eventId;
		this.eventType = eventType;
		this.appointmentId = appointmentId;
		this.payload = payload;
		this.status = OutboxEventStatus.PENDING;
		this.attempts = 0;
		this.nextAttemptAt = Instant.now();
	}

	@PrePersist
	protected void onCreate() {

		if (status == null) {
			status = OutboxEventStatus.PENDING;
		}

		if (nextAttemptAt == null) {
			nextAttemptAt = Instant.now();
		}

		if (attempts < 0) {
			throw new IllegalStateException("Outbox attempts cannot be negative.");
		}
	}
}