package com.medsync.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.entity.OutboxEventType;
import com.medsync.appointment.properties.OutboxDispatcherProperties;
import com.medsync.appointment.repository.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class OutboxEventStateServiceTests {

	private static final Long EVENT_ID = 1001L;

	private static final String EVENT_REFERENCE = "evt-12345678";

	private static final Long APPOINTMENT_ID = 501L;

	private static final Long CLAIMED_VERSION = 3L;

	@Mock
	private OutboxEventRepository outboxEventRepository;

	@InjectMocks
	private OutboxEventStateService outboxEventStateService;

	private OutboxDispatcherProperties properties;

	@BeforeEach
	void setUp() {

		properties = new OutboxDispatcherProperties();

		properties.setMaxAttempts(5);
		properties.setInitialRetryDelay(Duration.ofSeconds(5));
		properties.setMaxRetryDelay(Duration.ofSeconds(300));
	}

	@Test
	void markPublishedUpdatesClaimedEvent() {

		OutboxEvent event = createClaimedEvent(1);

		when(outboxEventRepository.markPublishedIfClaimed(eq(EVENT_ID), eq(CLAIMED_VERSION),
				org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(1);

		boolean result = outboxEventStateService.markPublished(event);

		assertThat(result).isTrue();

		ArgumentCaptor<Instant> processedAtCaptor = ArgumentCaptor.forClass(Instant.class);

		verify(outboxEventRepository).markPublishedIfClaimed(eq(EVENT_ID), eq(CLAIMED_VERSION),
				processedAtCaptor.capture());

		assertThat(processedAtCaptor.getValue()).isNotNull();
	}

	@Test
	void markPublishedReturnsFalseWhenClaimIsNoLongerCurrent() {

		OutboxEvent event = createClaimedEvent(1);

		when(outboxEventRepository.markPublishedIfClaimed(eq(EVENT_ID), eq(CLAIMED_VERSION),
				org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(0);

		boolean result = outboxEventStateService.markPublished(event);

		assertThat(result).isFalse();
	}

	@Test
	void recordFailureSchedulesFirstRetryAfterFiveSeconds() {

		OutboxEvent event = createClaimedEvent(1);

		Instant beforeFailure = Instant.now();

		when(outboxEventRepository.rescheduleIfClaimed(eq(EVENT_ID), eq(CLAIMED_VERSION),
				org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.anyString()))
				.thenReturn(1);

		boolean result = outboxEventStateService.recordFailure(event,
				new RuntimeException("Notification Service unavailable"), true, properties);

		Instant afterFailure = Instant.now();

		assertThat(result).isTrue();

		ArgumentCaptor<Instant> retryTimeCaptor = ArgumentCaptor.forClass(Instant.class);

		verify(outboxEventRepository).rescheduleIfClaimed(eq(EVENT_ID), eq(CLAIMED_VERSION), retryTimeCaptor.capture(),
				eq("RuntimeException: Notification Service unavailable"));

		assertThat(retryTimeCaptor.getValue()).isBetween(beforeFailure.plusSeconds(5), afterFailure.plusSeconds(5));
	}

	@Test
	void recordFailureUsesExponentialBackoffForSecondAttempt() {

		OutboxEvent event = createClaimedEvent(2);

		Instant beforeFailure = Instant.now();

		when(outboxEventRepository.rescheduleIfClaimed(eq(EVENT_ID), eq(CLAIMED_VERSION),
				org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.anyString()))
				.thenReturn(1);

		boolean result = outboxEventStateService.recordFailure(event, new RuntimeException("Temporary failure"), true,
				properties);

		Instant afterFailure = Instant.now();

		assertThat(result).isTrue();

		ArgumentCaptor<Instant> retryTimeCaptor = ArgumentCaptor.forClass(Instant.class);

		verify(outboxEventRepository).rescheduleIfClaimed(eq(EVENT_ID), eq(CLAIMED_VERSION), retryTimeCaptor.capture(),
				eq("RuntimeException: Temporary failure"));

		assertThat(retryTimeCaptor.getValue()).isBetween(beforeFailure.plusSeconds(10), afterFailure.plusSeconds(10));
	}

	@Test
	void recordFailureCapsRetryDelayAtConfiguredMaximum() {

		OutboxEvent event = createClaimedEvent(10);

		properties.setMaxAttempts(15);
		properties.setInitialRetryDelay(Duration.ofSeconds(5));
		properties.setMaxRetryDelay(Duration.ofSeconds(30));

		Instant beforeFailure = Instant.now();

		when(outboxEventRepository.rescheduleIfClaimed(eq(EVENT_ID), eq(CLAIMED_VERSION),
				org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.anyString()))
				.thenReturn(1);

		boolean result = outboxEventStateService.recordFailure(event, new RuntimeException("Temporary failure"), true,
				properties);

		Instant afterFailure = Instant.now();

		assertThat(result).isTrue();

		ArgumentCaptor<Instant> retryTimeCaptor = ArgumentCaptor.forClass(Instant.class);

		verify(outboxEventRepository).rescheduleIfClaimed(eq(EVENT_ID), eq(CLAIMED_VERSION), retryTimeCaptor.capture(),
				org.mockito.ArgumentMatchers.anyString());

		assertThat(retryTimeCaptor.getValue()).isBetween(beforeFailure.plusSeconds(30), afterFailure.plusSeconds(30));
	}

	@Test
	void recordFailureMarksEventFailedWhenMaximumAttemptsReached() {

		OutboxEvent event = createClaimedEvent(5);

		when(outboxEventRepository.markFailedIfClaimed(EVENT_ID, CLAIMED_VERSION,
				"RuntimeException: Notification Service unavailable")).thenReturn(1);

		boolean result = outboxEventStateService.recordFailure(event,
				new RuntimeException("Notification Service unavailable"), true, properties);

		assertThat(result).isTrue();

		verify(outboxEventRepository).markFailedIfClaimed(EVENT_ID, CLAIMED_VERSION,
				"RuntimeException: Notification Service unavailable");

		verify(outboxEventRepository, never()).rescheduleIfClaimed(org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(Instant.class),
				org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	void recordFailureMarksEventFailedWhenFailureIsPermanent() {

		OutboxEvent event = createClaimedEvent(1);

		when(outboxEventRepository.markFailedIfClaimed(EVENT_ID, CLAIMED_VERSION,
				"IllegalArgumentException: Invalid notification payload")).thenReturn(1);

		boolean result = outboxEventStateService.recordFailure(event,
				new IllegalArgumentException("Invalid notification payload"), false, properties);

		assertThat(result).isTrue();

		verify(outboxEventRepository).markFailedIfClaimed(EVENT_ID, CLAIMED_VERSION,
				"IllegalArgumentException: Invalid notification payload");

		verify(outboxEventRepository, never()).rescheduleIfClaimed(org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(Instant.class),
				org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	void recordFailureReturnsFalseWhenClaimIsNoLongerCurrent() {

		OutboxEvent event = createClaimedEvent(1);

		when(outboxEventRepository.rescheduleIfClaimed(eq(EVENT_ID), eq(CLAIMED_VERSION),
				org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.anyString()))
				.thenReturn(0);

		boolean result = outboxEventStateService.recordFailure(event, new RuntimeException("Temporary failure"), true,
				properties);

		assertThat(result).isFalse();
	}

	@Test
	void recordFailureTruncatesErrorMessageTo2000Characters() {

		OutboxEvent event = createClaimedEvent(1);

		String longMessage = "x".repeat(2500);

		when(outboxEventRepository.markFailedIfClaimed(eq(EVENT_ID), eq(CLAIMED_VERSION),
				org.mockito.ArgumentMatchers.anyString())).thenReturn(1);

		boolean result = outboxEventStateService.recordFailure(event, new RuntimeException(longMessage), false,
				properties);

		assertThat(result).isTrue();

		ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);

		verify(outboxEventRepository).markFailedIfClaimed(eq(EVENT_ID), eq(CLAIMED_VERSION), errorCaptor.capture());

		assertThat(errorCaptor.getValue()).hasSize(2000);
	}

	@Test
	void markPublishedRejectsNullEvent() {

		assertThatThrownBy(() -> outboxEventStateService.markPublished(null))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("persisted claimed outbox event");

		verifyNoInteractions(outboxEventRepository);
	}

	@Test
	void markPublishedRejectsEventWithoutVersion() {

		OutboxEvent event = createClaimedEvent(1);
		event.setVersion(null);

		assertThatThrownBy(() -> outboxEventStateService.markPublished(event))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");

		verifyNoInteractions(outboxEventRepository);
	}

	@Test
	void recordFailureRejectsNullFailure() {

		OutboxEvent event = createClaimedEvent(1);

		assertThatThrownBy(() -> outboxEventStateService.recordFailure(event, null, true, properties))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Failure details are required.");

		verifyNoInteractions(outboxEventRepository);
	}

	private OutboxEvent createClaimedEvent(int attempts) {

		OutboxEvent event = new OutboxEvent(EVENT_REFERENCE, OutboxEventType.APPOINTMENT_CONFIRMATION, APPOINTMENT_ID,
				"{}");

		event.setId(EVENT_ID);
		event.setVersion(CLAIMED_VERSION);
		event.setAttempts(attempts);

		return event;
	}
}