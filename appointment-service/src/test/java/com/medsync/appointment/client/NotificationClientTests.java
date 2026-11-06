package com.medsync.appointment.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.entity.OutboxEventType;

import tools.jackson.databind.json.JsonMapper;

class NotificationClientTests {

	private static final String NOTIFICATION_SERVICE_URL = "http://localhost:8085";
	private static final String NOTIFICATIONS_ENDPOINT = "/api/notifications";
	private static final String AUTHORIZATION_HEADER = "Bearer test-service-token";

	private static final String EVENT_ID = "evt-12345678";

	private static final Long APPOINTMENT_ID = 1001L;

	private static final String PAYLOAD = """
			{
			    "recipientEmail": "aarav.sharma@example.com",
			    "patientName": "Aarav Sharma",
			    "doctorName": "Dr. Meera Nair",
			    "appointmentDateTime": "2026-11-10T10:30:00",
			    "reason": "Routine cardiac consultation",
			    "appointmentReference": "APPT-1001",
			    "cancellationReason": null
			}
			""";

	private ServiceTokenProvider serviceTokenProvider;

	private MockRestServiceServer mockServer;

	private NotificationClient notificationClient;

	@BeforeEach
	void setUp() {

		serviceTokenProvider = mock(ServiceTokenProvider.class);

		RestClient.Builder restClientBuilder = RestClient.builder();

		mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();

		RestClient restClient = restClientBuilder.build();

		notificationClient = new NotificationClient(restClient, serviceTokenProvider, JsonMapper.builder().build(),
				NOTIFICATION_SERVICE_URL);

		when(serviceTokenProvider.getAuthorizationHeader()).thenReturn(AUTHORIZATION_HEADER);
	}

	static Stream<Arguments> notificationCases() {

		return Stream.of(Arguments.of(OutboxEventType.APPOINTMENT_CONFIRMATION, "CONFIRMATION", HttpStatus.CREATED),

				Arguments.of(OutboxEventType.APPOINTMENT_RESCHEDULED, "RESCHEDULED", HttpStatus.OK),

				Arguments.of(OutboxEventType.APPOINTMENT_CANCELLED, "CANCELLATION", HttpStatus.CREATED));
	}

	@ParameterizedTest
	@MethodSource("notificationCases")
	void createNotificationMapsEventAndSendsCorrectRequest(OutboxEventType eventType, String expectedNotificationType,
			HttpStatus responseStatus) {

		OutboxEvent event = createEvent(eventType);

		mockServer.expect(requestTo(NOTIFICATION_SERVICE_URL + NOTIFICATIONS_ENDPOINT))
				.andExpect(method(HttpMethod.POST)).andExpect(header(HttpHeaders.AUTHORIZATION, AUTHORIZATION_HEADER))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.idempotencyKey").value(EVENT_ID))
				.andExpect(jsonPath("$.type").value(expectedNotificationType))
				.andExpect(jsonPath("$.data.recipientEmail").value("aarav.sharma@example.com"))
				.andExpect(jsonPath("$.data.patientName").value("Aarav Sharma"))
				.andExpect(jsonPath("$.data.doctorName").value("Dr. Meera Nair"))
				.andExpect(jsonPath("$.data.appointmentDateTime").value("2026-11-10T10:30:00"))
				.andExpect(jsonPath("$.data.reason").value("Routine cardiac consultation"))
				.andExpect(jsonPath("$.data.appointmentReference").value("APPT-1001"))
				.andRespond(withStatus(responseStatus));

		notificationClient.createNotification(event);

		mockServer.verify();

		verify(serviceTokenProvider).getAuthorizationHeader();
	}

	@Test
	void createNotificationPreservesCancellationReason() {

		String cancellationPayload = """
				{
				    "recipientEmail": "aarav.sharma@example.com",
				    "patientName": "Aarav Sharma",
				    "doctorName": "Dr. Meera Nair",
				    "appointmentDateTime": "2026-11-10T10:30:00",
				    "reason": "Routine cardiac consultation",
				    "appointmentReference": "APPT-1001",
				    "cancellationReason": "Doctor unavailable due to emergency"
				}
				""";

		OutboxEvent event = new OutboxEvent(EVENT_ID, OutboxEventType.APPOINTMENT_CANCELLED, APPOINTMENT_ID,
				cancellationPayload);

		mockServer.expect(requestTo(NOTIFICATION_SERVICE_URL + NOTIFICATIONS_ENDPOINT))
				.andExpect(jsonPath("$.type").value("CANCELLATION"))
				.andExpect(jsonPath("$.data.cancellationReason").value("Doctor unavailable due to emergency"))
				.andRespond(withStatus(HttpStatus.CREATED));

		notificationClient.createNotification(event);

		mockServer.verify();
	}

	@Test
	void createNotificationPropagatesBadRequestResponse() {

		OutboxEvent event = createEvent(OutboxEventType.APPOINTMENT_CONFIRMATION);

		mockServer.expect(requestTo(NOTIFICATION_SERVICE_URL + NOTIFICATIONS_ENDPOINT))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST));

		assertThatThrownBy(() -> notificationClient.createNotification(event))
				.isInstanceOf(HttpClientErrorException.BadRequest.class);

		mockServer.verify();
	}

	@Test
	void createNotificationPropagatesIdempotencyConflict() {

		OutboxEvent event = createEvent(OutboxEventType.APPOINTMENT_CONFIRMATION);

		mockServer.expect(requestTo(NOTIFICATION_SERVICE_URL + NOTIFICATIONS_ENDPOINT))
				.andRespond(withStatus(HttpStatus.CONFLICT));

		assertThatThrownBy(() -> notificationClient.createNotification(event))
				.isInstanceOf(HttpClientErrorException.Conflict.class);

		mockServer.verify();
	}

	@Test
	void createNotificationPropagatesServerError() {

		OutboxEvent event = createEvent(OutboxEventType.APPOINTMENT_CONFIRMATION);

		mockServer.expect(requestTo(NOTIFICATION_SERVICE_URL + NOTIFICATIONS_ENDPOINT))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThatThrownBy(() -> notificationClient.createNotification(event))
				.isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);

		mockServer.verify();
	}

	@Test
	void createNotificationRejectsNullEvent() {

		assertThatThrownBy(() -> notificationClient.createNotification(null))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Outbox event is required.");

		verifyNoInteractions(serviceTokenProvider);
	}

	@Test
	void createNotificationRejectsInvalidIdempotencyKey() {

		OutboxEvent event = new OutboxEvent("invalid key!", OutboxEventType.APPOINTMENT_CONFIRMATION, APPOINTMENT_ID,
				PAYLOAD);

		assertThatThrownBy(() -> notificationClient.createNotification(event))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("invalid idempotency key");

		verifyNoInteractions(serviceTokenProvider);
	}

	@Test
	void createNotificationRejectsMissingPayload() {

		OutboxEvent event = new OutboxEvent(EVENT_ID, OutboxEventType.APPOINTMENT_CONFIRMATION, APPOINTMENT_ID, " ");

		assertThatThrownBy(() -> notificationClient.createNotification(event))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Outbox event payload is required.");

		verifyNoInteractions(serviceTokenProvider);
	}

	@Test
	void createNotificationRejectsMalformedJsonPayload() {

		OutboxEvent event = new OutboxEvent(EVENT_ID, OutboxEventType.APPOINTMENT_CONFIRMATION, APPOINTMENT_ID,
				"{ invalid json }");

		assertThatThrownBy(() -> notificationClient.createNotification(event))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Unable to deserialize the outbox notification payload");

		verifyNoInteractions(serviceTokenProvider);
	}

	@Test
	void createNotificationRejectsNullJsonPayload() {

		OutboxEvent event = new OutboxEvent(EVENT_ID, OutboxEventType.APPOINTMENT_CONFIRMATION, APPOINTMENT_ID, "null");

		assertThatThrownBy(() -> notificationClient.createNotification(event))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Outbox notification payload cannot be null.");

		verifyNoInteractions(serviceTokenProvider);
	}

	@Test
	void createNotificationReusesSameIdempotencyKeyOnRetry() {

		OutboxEvent event = createEvent(OutboxEventType.APPOINTMENT_CONFIRMATION);

		mockServer.expect(requestTo(NOTIFICATION_SERVICE_URL + NOTIFICATIONS_ENDPOINT))
				.andExpect(jsonPath("$.idempotencyKey").value(EVENT_ID)).andRespond(withStatus(HttpStatus.CREATED));

		mockServer.expect(requestTo(NOTIFICATION_SERVICE_URL + NOTIFICATIONS_ENDPOINT))
				.andExpect(jsonPath("$.idempotencyKey").value(EVENT_ID)).andRespond(withStatus(HttpStatus.OK));

		notificationClient.createNotification(event);
		notificationClient.createNotification(event);

		mockServer.verify();

		verify(serviceTokenProvider, org.mockito.Mockito.times(2)).getAuthorizationHeader();
	}

	private OutboxEvent createEvent(OutboxEventType eventType) {

		return new OutboxEvent(EVENT_ID, eventType, APPOINTMENT_ID, PAYLOAD);
	}
}