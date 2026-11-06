package com.medsync.notification.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.medsync.notification.dto.AppointmentEmailType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "notifications", indexes = { @Index(name = "idx_notification_status", columnList = "status"),
		@Index(name = "idx_notification_next_attempt", columnList = "next_attempt_at"),
		@Index(name = "idx_notification_provider_message", columnList = "provider_message_id"),
		@Index(name = "idx_notification_status_lease", columnList = "status, locked_until") })
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
	private String idempotencyKey;

	@Column(name = "recipient_email", nullable = false, length = 254)
	private String recipientEmail;

	@Column(nullable = false, length = 255)
	private String subject;

	@Enumerated(EnumType.STRING)
	@Column(name = "template_type", nullable = false, length = 30)
	private AppointmentEmailType templateType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationStatus status;

	@Column(name = "plain_text_content", columnDefinition = "TEXT")
	private String plainTextContent;

	@Column(name = "html_content", columnDefinition = "MEDIUMTEXT")
	private String htmlContent;

	@Column(name = "provider_message_id", length = 150)
	private String providerMessageId;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "next_attempt_at")
	private Instant nextAttemptAt;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	@Column(name = "delivered_at")
	private Instant deliveredAt;

	@Column(name = "last_error", length = 1000)
	private String lastError;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Long version;

	protected Notification() {
	}

	public Notification(String idempotencyKey, String recipientEmail, String subject, AppointmentEmailType templateType,
			String plainTextContent, String htmlContent) {

		this.idempotencyKey = idempotencyKey;
		this.recipientEmail = recipientEmail;
		this.subject = subject;
		this.templateType = templateType;
		this.plainTextContent = plainTextContent;
		this.htmlContent = htmlContent;
		this.status = NotificationStatus.PENDING;
		this.attemptCount = 0;
	}

	public void markProcessing(Instant leaseUntil) {
		if (leaseUntil == null || !leaseUntil.isAfter(Instant.now())) {
			throw new IllegalArgumentException("Processing lease must be in the future.");
		}

		this.status = NotificationStatus.PROCESSING;
		this.attemptCount++;
		this.nextAttemptAt = null;
		this.lockedUntil = leaseUntil;
		this.lastError = null;
	}

	public void markAccepted(String providerMessageId) {
		this.status = NotificationStatus.ACCEPTED;
		this.providerMessageId = providerMessageId;
		this.acceptedAt = Instant.now();
		this.lastError = null;
		this.nextAttemptAt = null;
		this.lockedUntil = null;
	}

	public void markDelivered() {
		this.status = NotificationStatus.DELIVERED;
		this.deliveredAt = Instant.now();
		this.lastError = null;
		this.lockedUntil = null;
	}

	public void markFailed(String error, Instant retryAt) {
		this.status = NotificationStatus.FAILED;
		this.lastError = truncateError(error);
		this.nextAttemptAt = retryAt;
		this.lockedUntil = null;
	}

	public void markBounced(String error) {
		this.status = NotificationStatus.BOUNCED;
		this.lastError = truncateError(error);
		this.nextAttemptAt = null;
		this.lockedUntil = null;
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

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getRecipientEmail() {
		return recipientEmail;
	}

	public String getSubject() {
		return subject;
	}

	public AppointmentEmailType getTemplateType() {
		return templateType;
	}

	public NotificationStatus getStatus() {
		return status;
	}

	public String getPlainTextContent() {
		return plainTextContent;
	}

	public String getHtmlContent() {
		return htmlContent;
	}

	public String getProviderMessageId() {
		return providerMessageId;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}

	public Instant getAcceptedAt() {
		return acceptedAt;
	}

	public Instant getDeliveredAt() {
		return deliveredAt;
	}

	public String getLastError() {
		return lastError;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Long getVersion() {
		return version;
	}
}