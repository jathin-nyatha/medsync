package com.medsync.appointment.service;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import com.medsync.appointment.client.NotificationClient;
import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.observability.OutboxMetrics;
import com.medsync.appointment.properties.OutboxDispatcherProperties;

@Service
public class OutboxEventDeliveryService {

	private final NotificationClient notificationClient;
	private final OutboxEventStateService outboxEventStateService;
	private final OutboxDispatcherProperties properties;
	private final OutboxMetrics outboxMetrics;

	public OutboxEventDeliveryService(NotificationClient notificationClient,
			OutboxEventStateService outboxEventStateService, OutboxDispatcherProperties properties,
			OutboxMetrics outboxMetrics) {
		this.notificationClient = notificationClient;
		this.outboxEventStateService = outboxEventStateService;
		this.properties = properties;
		this.outboxMetrics = outboxMetrics;
	}

	public void deliver(OutboxEvent event) {
		long startedAt = System.nanoTime();
		try {
			try {
				notificationClient.createNotification(event);
			} catch (RuntimeException failure) {
				boolean retryable = isRetryable(failure);
				outboxEventStateService.recordFailure(event, failure, retryable, properties);
				return;
			}
			outboxEventStateService.markPublished(event);
		} finally {
			outboxMetrics.recordDeliveryDuration(Duration.ofNanos(System.nanoTime() - startedAt));
		}
	}

	boolean isRetryable(Throwable failure) {
		HttpStatusCodeException httpFailure = findHttpStatusFailure(failure);
		if (httpFailure != null) {
			int statusCode = httpFailure.getStatusCode().value();
			if (statusCode == HttpStatus.REQUEST_TIMEOUT.value()
					|| statusCode == HttpStatus.TOO_MANY_REQUESTS.value()) {
				return true;
			}
			if (statusCode >= 500 && statusCode <= 599) {
				return true;
			}
			if (statusCode >= 400 && statusCode <= 499) {
				return false;
			}
		}

		if (failure instanceof IllegalArgumentException) {
			return false;
		}
		if (failure instanceof IllegalStateException) {
			return hasRestClientCause(failure);
		}
		if (failure instanceof ResourceAccessException || failure instanceof RestClientException) {
			return true;
		}
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