package com.medsync.appointment.client;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.medsync.appointment.client.dto.AppointmentEmailData;
import com.medsync.appointment.client.dto.CreateNotificationRequest;
import com.medsync.appointment.client.dto.NotificationType;
import com.medsync.appointment.dto.AppointmentNotificationPayload;
import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.entity.OutboxEventType;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationClient {

	private static final String NOTIFICATIONS_ENDPOINT = "/api/notifications";

	private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{8,100}$");

	private final RestClient restClient;
	private final ServiceTokenProvider serviceTokenProvider;
	private final ObjectMapper objectMapper;
	private final String notificationServiceUrl;

	public NotificationClient(RestClient medSyncRestClient, ServiceTokenProvider serviceTokenProvider,
			ObjectMapper objectMapper,
			@Value("${medsync.clients.notification-service-url:http://localhost:8085}") String notificationServiceUrl) {

		this.restClient = medSyncRestClient;
		this.serviceTokenProvider = serviceTokenProvider;
		this.objectMapper = objectMapper;
		this.notificationServiceUrl = removeTrailingSlash(notificationServiceUrl);
	}

	/**
	 * Creates a notification in Notification Service.
	 *
	 * The outbox event ID is reused as the idempotency key. Therefore, retries of
	 * the same event cannot create duplicate notification records when the
	 * receiving service honors its idempotency contract.
	 *
	 * HTTP exceptions deliberately propagate to the caller.
	 */
	public void createNotification(OutboxEvent event) {

		validateEvent(event);

		AppointmentNotificationPayload payload = deserializePayload(event.getPayload());

		NotificationType notificationType = mapNotificationType(event.getEventType());

		CreateNotificationRequest request = new CreateNotificationRequest(event.getEventId(), notificationType,
				new AppointmentEmailData(payload.recipientEmail(), payload.patientName(), payload.doctorName(),
						payload.appointmentDateTime(), payload.reason(), payload.appointmentReference(),
						payload.cancellationReason()));

		String authorizationHeader = serviceTokenProvider.getAuthorizationHeader();

		restClient.post().uri(notificationServiceUrl + NOTIFICATIONS_ENDPOINT)
				.header(HttpHeaders.AUTHORIZATION, authorizationHeader).contentType(MediaType.APPLICATION_JSON)
				.body(request).retrieve().toBodilessEntity();
	}

	private void validateEvent(OutboxEvent event) {

		if (event == null) {
			throw new IllegalArgumentException("Outbox event is required.");
		}

		if (event.getEventId() == null || !IDEMPOTENCY_KEY_PATTERN.matcher(event.getEventId()).matches()) {

			throw new IllegalArgumentException("Outbox event contains an invalid idempotency key.");
		}

		if (event.getEventType() == null) {
			throw new IllegalArgumentException("Outbox event type is required.");
		}

		if (event.getPayload() == null || event.getPayload().isBlank()) {
			throw new IllegalArgumentException("Outbox event payload is required.");
		}
	}

	private AppointmentNotificationPayload deserializePayload(String payload) {

		try {

			AppointmentNotificationPayload result = objectMapper.readValue(payload,
					AppointmentNotificationPayload.class);

			if (result == null) {
				throw new IllegalArgumentException("Outbox notification payload cannot be null.");
			}

			return result;

		} catch (JacksonException exception) {

			throw new IllegalArgumentException("Unable to deserialize the outbox notification payload.", exception);
		}
	}

	private NotificationType mapNotificationType(OutboxEventType eventType) {

		return switch (eventType) {
		case APPOINTMENT_CONFIRMATION -> NotificationType.CONFIRMATION;
		case APPOINTMENT_RESCHEDULED -> NotificationType.RESCHEDULED;
		case APPOINTMENT_CANCELLED -> NotificationType.CANCELLATION;
		};
	}

	private String removeTrailingSlash(String url) {

		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("Notification Service URL must be configured.");
		}

		String normalizedUrl = url.trim();

		while (normalizedUrl.endsWith("/")) {
			normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
		}

		return normalizedUrl;
	}
}