package com.medsync.notification.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_attempts", indexes = {
		@Index(name = "idx_attempt_notification", columnList = "notification_id") })
public class NotificationAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "notification_id", nullable = false)
	private Notification notification;

	@Column(name = "attempt_number", nullable = false)
	private int attemptNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private DeliveryAttemptStatus status;

	@Column(name = "provider_status_code")
	private Integer providerStatusCode;

	@Column(name = "provider_message_id", length = 150)
	private String providerMessageId;

	@Column(name = "error_message", length = 1000)
	private String errorMessage;

	@CreationTimestamp
	@Column(name = "attempted_at", nullable = false, updatable = false)
	private Instant attemptedAt;

	protected NotificationAttempt() {
	}

	public NotificationAttempt(Notification notification, int attemptNumber, DeliveryAttemptStatus status,
			Integer providerStatusCode, String providerMessageId, String errorMessage) {
		this.notification = notification;
		this.attemptNumber = attemptNumber;
		this.status = status;
		this.providerStatusCode = providerStatusCode;
		this.providerMessageId = providerMessageId;
		this.errorMessage = truncateError(errorMessage);
	}

	private String truncateError(String error) {

		if (error == null) {
			return null;
		}

		return error.length() > 1000 ? error.substring(0, 1000) : error;
	}

	public Long getId() {
		return id;
	}

	public Notification getNotification() {
		return notification;
	}

	public int getAttemptNumber() {
		return attemptNumber;
	}

	public DeliveryAttemptStatus getStatus() {
		return status;
	}

	public Integer getProviderStatusCode() {
		return providerStatusCode;
	}

	public String getProviderMessageId() {
		return providerMessageId;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Instant getAttemptedAt() {
		return attemptedAt;
	}
}