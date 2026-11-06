
package com.medsync.appointment.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
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

	boolean isRetryable(Throwable failure) {

		HttpStatusCodeException httpFailure = findHttpStatusFailure(failure);

		if (httpFailure != null) {

			int statusCode = httpFailure.getStatusCode().value();

			// These HTTP client errors can represent temporary conditions.
			if (statusCode == HttpStatus.REQUEST_TIMEOUT.value()
					|| statusCode == HttpStatus.TOO_MANY_REQUESTS.value()) {
				return true;
			}

			// Retry server-side HTTP failures.
			if (statusCode >= 500 && statusCode <= 599) {
				return true;
			}

			// Other 4xx responses are permanent by default.
			if (statusCode >= 400 && statusCode <= 499) {
				return false;
			}
		}

		if (failure instanceof IllegalArgumentException) {
			// Invalid payload or idempotency key.
			return false;
		}

		if (failure instanceof IllegalStateException) {
			// Token configuration/validation errors are permanent.
			// Wrapped HTTP and network errors were classified above.
			return hasRestClientCause(failure);
		}

		if (failure instanceof ResourceAccessException || failure instanceof RestClientException) {
			return true;
		}

		// Unexpected runtime failures receive bounded retries.
		return true;
	}

	private HttpStatusCodeException findHttpStatusFailure(Throwable failure) {

		Throwable current = failure;

		while (current != null) {

			if (current instanceof HttpStatusCodeException httpFailure) {
				return httpFailure;
			}

			current = current.getCause();
		}

		return null;
	}

	private boolean hasRestClientCause(Throwable failure) {

		Throwable current = failure.getCause();

		while (current != null) {

			if (current instanceof RestClientException) {
				return true;
			}

			current = current.getCause();
		}

		return false;
	}
}
