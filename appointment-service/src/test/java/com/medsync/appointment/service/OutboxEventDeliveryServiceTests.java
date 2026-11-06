
package com.medsync.appointment.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import com.medsync.appointment.client.NotificationClient;
import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.properties.OutboxDispatcherProperties;

@ExtendWith(MockitoExtension.class)
class OutboxEventDeliveryServiceTests {

	@Mock
	private NotificationClient notificationClient;

	@Mock
	private OutboxEventStateService outboxEventStateService;

	@Mock
	private OutboxDispatcherProperties properties;

	private OutboxEventDeliveryService deliveryService;

	private OutboxEvent event;

	@BeforeEach
	void setUp() {
		deliveryService = new OutboxEventDeliveryService(notificationClient, outboxEventStateService, properties);

		event = mock(OutboxEvent.class);
	}

	@Test
	void deliverMarksEventPublishedWhenNotificationSucceeds() {

		deliveryService.deliver(event);

		verify(notificationClient).createNotification(event);
		verify(outboxEventStateService).markPublished(event);
		verifyNoMoreInteractions(outboxEventStateService);
	}

	@Test
	void deliverRetriesWhenNotificationServiceReturns408() {

		assertRetryableHttpFailure(HttpStatus.REQUEST_TIMEOUT);
	}

	@Test
	void deliverRetriesWhenNotificationServiceReturns429() {

		assertRetryableHttpFailure(HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	void deliverDoesNotRetryWhenNotificationServiceReturns400() {

		assertPermanentHttpFailure(HttpStatus.BAD_REQUEST);
	}

	@Test
	void deliverDoesNotRetryWhenNotificationServiceReturns409() {

		assertPermanentHttpFailure(HttpStatus.CONFLICT);
	}

	@Test
	void deliverRetriesWhenNotificationServiceReturns503() {

		HttpServerErrorException failure = new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE);

		assertFailureHandled(failure, true);
	}

	@Test
	void deliverRetriesWhenNetworkConnectionFails() {

		ResourceAccessException failure = new ResourceAccessException("Notification Service connection failed",
				new IOException("Connection refused"));

		assertFailureHandled(failure, true);
	}

	@Test
	void deliverRetriesWhenRestClientFails() {

		RestClientException failure = new RestClientException("Unexpected REST client failure");

		assertFailureHandled(failure, true);
	}

	@Test
	void deliverDoesNotRetryWhenPayloadIsInvalid() {

		IllegalArgumentException failure = new IllegalArgumentException("Invalid notification payload");

		assertFailureHandled(failure, false);
	}

	@Test
	void deliverRetriesWhenServiceTokenFailureHasNetworkCause() {

		IllegalStateException failure = new IllegalStateException("Unable to obtain service token",
				new ResourceAccessException("Auth Service unavailable"));

		assertFailureHandled(failure, true);
	}

	@Test
	void deliverDoesNotRetryWhenServiceTokenFailureIsNotNetworkRelated() {

		IllegalStateException failure = new IllegalStateException("Invalid service credentials");

		assertFailureHandled(failure, false);
	}

	@Test
	void deliverRetriesUnexpectedRuntimeFailures() {

		RuntimeException failure = new RuntimeException("Unexpected delivery failure");

		assertFailureHandled(failure, true);
	}

	private void assertRetryableHttpFailure(HttpStatus status) {

		assertFailureHandled(createClientError(status), true);
	}

	private void assertPermanentHttpFailure(HttpStatus status) {

		assertFailureHandled(createClientError(status), false);
	}

	private HttpClientErrorException createClientError(HttpStatus status) {

		return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY, new byte[0],
				StandardCharsets.UTF_8);
	}

	private void assertFailureHandled(RuntimeException failure, boolean retryable) {

		doThrow(failure).when(notificationClient).createNotification(event);

		deliveryService.deliver(event);

		verify(notificationClient).createNotification(event);

		verify(outboxEventStateService).recordFailure(event, failure, retryable, properties);

		verifyNoMoreInteractions(outboxEventStateService);
	}
}
