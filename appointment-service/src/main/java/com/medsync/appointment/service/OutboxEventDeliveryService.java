package com.medsync.appointment.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import com.medsync.appointment.client.NotificationClient;
import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.properties.OutboxDispatcherProperties;

@Service
public class OutboxEventDeliveryService {

	private final NotificationClient notificationClient;
	private final OutboxEventStateService outboxEventStateService;
	private final OutboxDispatcherProperties properties;

	public OutboxEventDeliveryService(NotificationClient notificationClient,
			OutboxEventStateService outboxEventStateService, OutboxDispatcherProperties properties) {

		this.notificationClient = notificationClient;
		this.outboxEventStateService = outboxEventStateService;
		this.properties = properties;
	}

	public void deliver(OutboxEvent event) {

		try {
			notificationClient.createNotification(event);

		} catch (RuntimeException failure) {

			boolean retryable = isRetryable(failure);

			outboxEventStateService.recordFailure(event, failure, retryable, properties);

			return;
		}

		outboxEventStateService.markPublished(event);
	}

	private boolean isRetryable(Throwable failure) {

		if (failure instanceof HttpClientErrorException clientError) {

			int statusCode = clientError.getStatusCode().value();

			// Transient client-side conditions.
			if (statusCode == HttpStatus.REQUEST_TIMEOUT.value()
					|| statusCode == HttpStatus.TOO_MANY_REQUESTS.value()) {
				return true;
			}

			// Other 4xx responses, including 400 and 409,
			// are treated as permanent failures by default.
			return false;
		}

		if (failure instanceof HttpServerErrorException || failure instanceof ResourceAccessException
				|| failure instanceof RestClientException) {
			return true;
		}

		if (failure instanceof IllegalArgumentException) {
			// Invalid payload or invalid idempotency key.
			return false;
		}

		if (failure instanceof IllegalStateException) {
			// Service-token network failures may be wrapped by ServiceTokenProvider.
			return hasRestClientCause(failure);
		}

		// Unexpected runtime failures receive bounded retries.
		return true;
	}

	private boolean hasRestClientCause(Throwable failure) {

		Throwable current = failure.getCause();

		while (current != null && current != failure) {

			if (current instanceof RestClientException) {
				return true;
			}

			current = current.getCause();
		}

		return false;
	}
}