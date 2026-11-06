
package com.medsync.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.medsync.notification.dto.AppointmentEmailType;
import com.medsync.notification.entity.Notification;
import com.medsync.notification.entity.NotificationStatus;

class NotificationEntityTests {

	private static final long LEASE_DURATION_SECONDS = 300;

	@Test
	void shouldCreateNotificationWithPendingStatus() {

		Notification notification = createNotification();

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(notification.getAttemptCount()).isZero();
		assertThat(notification.getIdempotencyKey()).isEqualTo("appointment-confirmation-1001");
		assertThat(notification.getLockedUntil()).isNull();
	}

	@Test
	void shouldMarkNotificationAsProcessingAndSetLease() {

		Notification notification = createNotification();

		Instant leaseUntil = Instant.now().plusSeconds(LEASE_DURATION_SECONDS);

		notification.markProcessing(leaseUntil);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
		assertThat(notification.getAttemptCount()).isEqualTo(1);
		assertThat(notification.getLastError()).isNull();
		assertThat(notification.getLockedUntil()).isEqualTo(leaseUntil);
		assertThat(notification.getNextAttemptAt()).isNull();
	}

	@Test
	void shouldRejectNullOrExpiredProcessingLease() {

		Notification notification = createNotification();

		assertThatThrownBy(() -> notification.markProcessing(null)).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> notification.markProcessing(Instant.now().minusSeconds(1)))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(notification.getAttemptCount()).isZero();
	}

	@Test
	void shouldMarkNotificationAsAcceptedAndClearLease() {

		Notification notification = createNotification();

		notification.markProcessing(Instant.now().plusSeconds(LEASE_DURATION_SECONDS));
		notification.markAccepted("ses-message-123");

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.ACCEPTED);
		assertThat(notification.getProviderMessageId()).isEqualTo("ses-message-123");
		assertThat(notification.getAcceptedAt()).isNotNull();
		assertThat(notification.getLockedUntil()).isNull();
		assertThat(notification.getNextAttemptAt()).isNull();
	}

	@Test
	void shouldMarkNotificationAsDelivered() {

		Notification notification = createNotification();

		notification.markProcessing(Instant.now().plusSeconds(LEASE_DURATION_SECONDS));
		notification.markAccepted("ses-message-123");
		notification.markDelivered();

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
		assertThat(notification.getDeliveredAt()).isNotNull();
		assertThat(notification.getLockedUntil()).isNull();
	}

	@Test
	void shouldMarkNotificationAsFailedAndScheduleRetryAndClearLease() {

		Notification notification = createNotification();

		Instant leaseUntil = Instant.now().plusSeconds(LEASE_DURATION_SECONDS);
		Instant retryAt = Instant.now().plusSeconds(60);

		notification.markProcessing(leaseUntil);
		notification.markFailed("Temporary provider error", retryAt);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
		assertThat(notification.getLastError()).isEqualTo("Temporary provider error");
		assertThat(notification.getNextAttemptAt()).isEqualTo(retryAt);
		assertThat(notification.getLockedUntil()).isNull();
	}

	@Test
	void shouldMarkNotificationAsBouncedWithoutRetryAndClearLease() {

		Notification notification = createNotification();

		notification.markProcessing(Instant.now().plusSeconds(LEASE_DURATION_SECONDS));
		notification.markAccepted("ses-message-123");
		notification.markBounced("Recipient address rejected");

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.BOUNCED);
		assertThat(notification.getNextAttemptAt()).isNull();
		assertThat(notification.getLockedUntil()).isNull();
	}

	@Test
	void shouldTruncateLongErrorMessages() {

		Notification notification = createNotification();

		String longError = "E".repeat(1200);

		notification.markFailed(longError, null);

		assertThat(notification.getLastError()).hasSize(1000);
	}

	private Notification createNotification() {

		return new Notification("appointment-confirmation-1001", "patient@example.com",
				"MedSync - Appointment Confirmed", AppointmentEmailType.CONFIRMATION, "Your appointment is confirmed.",
				"<html><body>Appointment confirmed.</body></html>");
	}
}
