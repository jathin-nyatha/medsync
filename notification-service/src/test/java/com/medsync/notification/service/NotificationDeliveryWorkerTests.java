
package com.medsync.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medsync.notification.entity.Notification;
import com.medsync.notification.exception.EmailProviderException;
import com.medsync.notification.repository.NotificationRepository;

import software.amazon.awssdk.core.exception.SdkClientException;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryWorkerTests {

	private static final Long NOTIFICATION_ID = 1001L;
	private static final int BATCH_SIZE = 25;

	@Mock
	private NotificationRepository notificationRepository;

	@Mock
	private NotificationClaimService claimService;

	@Mock
	private NotificationDeliveryResultService resultService;

	@Mock
	private EmailProvider emailProvider;

	@Mock
	private Notification notification;

	private NotificationRetryPolicy retryPolicy;
	private NotificationDeliveryWorker worker;

	@BeforeEach
	void setUp() {
		retryPolicy = new NotificationRetryPolicy(5, 30, 21600);

		worker = new NotificationDeliveryWorker(notificationRepository, claimService, resultService, retryPolicy,
				emailProvider, BATCH_SIZE);

		// This query is used by all five tests.
		when(notificationRepository.findEligibleNotificationIds(any(Instant.class), eq(BATCH_SIZE)))
				.thenReturn(List.of(NOTIFICATION_ID));
	}

	private void stubSuccessfullyClaimedNotification() {

		when(claimService.claim(eq(NOTIFICATION_ID), any(Instant.class))).thenReturn(Optional.of(notification));

		when(notification.getId()).thenReturn(NOTIFICATION_ID);
		when(notification.getAttemptCount()).thenReturn(1);
		when(notification.getRecipientEmail()).thenReturn("patient@example.com");
		when(notification.getSubject()).thenReturn("MedSync - Appointment Confirmed");
		when(notification.getPlainTextContent()).thenReturn("Your appointment has been confirmed.");
		when(notification.getHtmlContent()).thenReturn("<p>Your appointment has been confirmed.</p>");
	}

	@Test
	void shouldRecordAcceptedWhenEmailProviderSucceeds() {

		stubSuccessfullyClaimedNotification();

		when(emailProvider.send(any())).thenReturn(new EmailProvider.EmailDeliveryResult(200, "ses-message-123"));

		worker.processPendingNotifications();

		verify(resultService).recordAccepted(NOTIFICATION_ID, 1, 200, "ses-message-123");

		verify(resultService, never()).recordFailure(anyLong(), anyInt(), nullable(Integer.class), anyString(),
				nullable(Instant.class));
	}

	@Test
	void shouldScheduleRetryForClientSideSdkFailure() {

		stubSuccessfullyClaimedNotification();

		when(emailProvider.send(any())).thenThrow(
				new EmailProviderException("SES request failed.", SdkClientException.create("Connection timed out.")));

		worker.processPendingNotifications();

		ArgumentCaptor<Instant> retryAtCaptor = ArgumentCaptor.forClass(Instant.class);

		verify(resultService).recordFailure(eq(NOTIFICATION_ID), eq(1), isNull(), any(String.class),
				retryAtCaptor.capture());

		org.junit.jupiter.api.Assertions.assertTrue(retryAtCaptor.getValue().isAfter(Instant.now()));

		verify(resultService, never()).recordAccepted(anyLong(), anyInt(), anyInt(), anyString());
	}

	@Test
	void shouldNotRetryUnknownPermanentFailure() {

		stubSuccessfullyClaimedNotification();

		when(emailProvider.send(any())).thenThrow(new IllegalArgumentException("Invalid email content."));

		worker.processPendingNotifications();

		verify(resultService).recordFailure(eq(NOTIFICATION_ID), eq(1), isNull(), any(String.class), isNull());
	}

	@Test
	void shouldNotRetryWhenMaximumAttemptsReached() {

		stubSuccessfullyClaimedNotification();

		when(notification.getAttemptCount()).thenReturn(5);

		when(emailProvider.send(any())).thenThrow(
				new EmailProviderException("SES request failed.", SdkClientException.create("Connection timed out.")));

		worker.processPendingNotifications();

		verify(resultService).recordFailure(eq(NOTIFICATION_ID), eq(5), isNull(), any(String.class), isNull());
	}

	@Test
	void shouldNotSendWhenNotificationCannotBeClaimed() {

		// Deliberately return empty. No Notification properties
		// should be stubbed because the worker must exit before reading them.
		when(claimService.claim(eq(NOTIFICATION_ID), any(Instant.class))).thenReturn(Optional.empty());

		worker.processPendingNotifications();

		verify(emailProvider, never()).send(any());

		verify(resultService, never()).recordAccepted(anyLong(), anyInt(), anyInt(), anyString());

		verify(resultService, never()).recordFailure(anyLong(), anyInt(), nullable(Integer.class), anyString(),
				nullable(Instant.class));
	}
}
