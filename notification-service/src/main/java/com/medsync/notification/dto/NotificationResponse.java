package com.medsync.notification.dto;

import java.time.Instant;

import com.medsync.notification.entity.Notification;
import com.medsync.notification.entity.NotificationStatus;

public record NotificationResponse(Long id, String recipientEmail, String subject, AppointmentEmailType templateType,
		NotificationStatus status, String providerMessageId, int attemptCount, Instant nextAttemptAt,
		Instant acceptedAt, Instant deliveredAt, String lastError, Instant createdAt, Instant updatedAt) {

	public static NotificationResponse from(Notification notification) {
		return new NotificationResponse(notification.getId(), notification.getRecipientEmail(),
				notification.getSubject(), notification.getTemplateType(), notification.getStatus(),
				notification.getProviderMessageId(), notification.getAttemptCount(), notification.getNextAttemptAt(),
				notification.getAcceptedAt(), notification.getDeliveredAt(), notification.getLastError(),
				notification.getCreatedAt(), notification.getUpdatedAt());
	}
}