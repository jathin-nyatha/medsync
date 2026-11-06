package com.medsync.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.medsync.notification.repository.NotificationAttemptRepository;
import com.medsync.notification.repository.NotificationRepository;

@SpringBootTest(properties = { "spring.datasource.url=jdbc:mysql://localhost:3306/medsync_notification_test",
		"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
		"spring.flyway.baseline-on-migrate=false", "spring.jpa.show-sql=false",
		"medsync.notification.ses.enabled=false" })
@AutoConfigureMockMvc
class NotificationApiIntegrationTests {

	private static final String NOTIFICATIONS_URL = "/api/notifications";

	private static final String VALID_REQUEST = """
			{
			  "idempotencyKey": "appointment-1001-confirmation-v1",
			  "type": "CONFIRMATION",
			  "data": {
			    "recipientEmail": "patient@example.com",
			    "patientName": "Arjun Sharma",
			    "doctorName": "Dr. Priya Reddy",
			    "appointmentDateTime": "10 November 2026, 10:30 AM",
			    "reason": "General consultation",
			    "appointmentReference": "APT-1001",
			    "cancellationReason": null
			  }
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private NotificationAttemptRepository notificationAttemptRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {

		notificationAttemptRepository.deleteAll();
		notificationRepository.deleteAll();
	}

	// ---------------------------------------------------------
	// Notification creation
	// ---------------------------------------------------------

	@Test
	void shouldCreateNotificationAndPersistIt() throws Exception {

		mockMvc.perform(
				post(NOTIFICATIONS_URL).with(adminJwt()).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.recipientEmail").value("patient@example.com"))
				.andExpect(jsonPath("$.templateType").value("CONFIRMATION"))
				.andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.attemptCount").value(0))
				.andExpect(jsonPath("$.createdAt").exists());

		assertThat(countByIdempotencyKey("appointment-1001-confirmation-v1")).isEqualTo(1);
	}

	@Test
	void shouldReturnExistingNotificationForIdenticalRetry() throws Exception {

		MvcResult firstResult = mockMvc.perform(
				post(NOTIFICATIONS_URL).with(adminJwt()).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
				.andExpect(status().isCreated()).andReturn();

		Integer firstId = JsonPath.read(firstResult.getResponse().getContentAsString(), "$.id");

		MvcResult secondResult = mockMvc.perform(
				post(NOTIFICATIONS_URL).with(adminJwt()).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
				.andExpect(status().isOk()).andReturn();

		Integer secondId = JsonPath.read(secondResult.getResponse().getContentAsString(), "$.id");

		assertThat(secondId).isEqualTo(firstId);

		assertThat(countByIdempotencyKey("appointment-1001-confirmation-v1")).isEqualTo(1);
	}

	@Test
	void shouldRejectSameIdempotencyKeyWithDifferentPayload() throws Exception {

		mockMvc.perform(
				post(NOTIFICATIONS_URL).with(adminJwt()).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
				.andExpect(status().isCreated());

		String modifiedRequest = VALID_REQUEST.replace("patient@example.com", "different.patient@example.com");

		mockMvc.perform(post(NOTIFICATIONS_URL).with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
				.content(modifiedRequest)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Idempotency conflict"));

		assertThat(countByIdempotencyKey("appointment-1001-confirmation-v1")).isEqualTo(1);
	}

	@Test
	void shouldHandleConcurrentRequestsWithSameIdempotencyKey() throws Exception {

		int numberOfRequests = 2;

		ExecutorService executor = Executors.newFixedThreadPool(numberOfRequests);

		CountDownLatch ready = new CountDownLatch(numberOfRequests);
		CountDownLatch start = new CountDownLatch(1);

		try {
			Future<MvcResult> firstRequest = executor.submit(() -> {
				ready.countDown();
				start.await();

				return mockMvc.perform(post(NOTIFICATIONS_URL).with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
						.content(VALID_REQUEST)).andReturn();
			});

			Future<MvcResult> secondRequest = executor.submit(() -> {
				ready.countDown();
				start.await();

				return mockMvc.perform(post(NOTIFICATIONS_URL).with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
						.content(VALID_REQUEST)).andReturn();
			});

			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

			start.countDown();

			MvcResult first = firstRequest.get(20, TimeUnit.SECONDS);
			MvcResult second = secondRequest.get(20, TimeUnit.SECONDS);

			int firstStatus = first.getResponse().getStatus();
			int secondStatus = second.getResponse().getStatus();

			assertThat(firstStatus).isIn(200, 201);
			assertThat(secondStatus).isIn(200, 201);

			assertThat(firstStatus + secondStatus).isEqualTo(401);

			Integer firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");

			Integer secondId = JsonPath.read(second.getResponse().getContentAsString(), "$.id");

			assertThat(firstId).isEqualTo(secondId);

			assertThat(countByIdempotencyKey("appointment-1001-confirmation-v1")).isEqualTo(1);

		} finally {
			executor.shutdownNow();
		}
	}

	// ---------------------------------------------------------
	// Notification retrieval
	// ---------------------------------------------------------

	@Test
	void shouldRetrieveExistingNotification() throws Exception {

		MvcResult creationResult = mockMvc.perform(
				post(NOTIFICATIONS_URL).with(adminJwt()).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
				.andExpect(status().isCreated()).andReturn();

		Integer notificationId = JsonPath.read(creationResult.getResponse().getContentAsString(), "$.id");

		mockMvc.perform(get(NOTIFICATIONS_URL + "/" + notificationId).with(receptionistJwt()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(notificationId))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.recipientEmail").value("patient@example.com"));
	}

	@Test
	void shouldReturnNotFoundForUnknownNotification() throws Exception {

		mockMvc.perform(get(NOTIFICATIONS_URL + "/999999").with(adminJwt())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Notification not found"));
	}

	@Test
	void shouldReturnPaginatedNotifications() throws Exception {

		createNotification("appointment-1001-confirmation-v1", "patient1@example.com");

		createNotification("appointment-1002-confirmation-v1", "patient2@example.com");

		mockMvc.perform(get(NOTIFICATIONS_URL).with(adminJwt()).param("page", "0").param("size", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	void shouldFilterNotificationsByStatus() throws Exception {

		createNotification("appointment-1001-confirmation-v1", "patient1@example.com");

		createNotification("appointment-1002-confirmation-v1", "patient2@example.com");

		mockMvc.perform(get(NOTIFICATIONS_URL).with(adminJwt()).param("status", "PENDING")).andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].status").value("PENDING"));
	}

	// ---------------------------------------------------------
	// Request validation
	// ---------------------------------------------------------

	@Test
	void shouldRejectInvalidEmailAddress() throws Exception {

		String invalidRequest = VALID_REQUEST.replace("patient@example.com", "not-an-email");

		mockMvc.perform(post(NOTIFICATIONS_URL).with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors['data.recipientEmail']").exists());
	}

	@Test
	void shouldRejectMissingIdempotencyKey() throws Exception {

		String invalidRequest = VALID_REQUEST.replace("\"idempotencyKey\": \"appointment-1001-confirmation-v1\",", "");

		mockMvc.perform(post(NOTIFICATIONS_URL).with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.idempotencyKey").exists());
	}

	@Test
	void shouldRejectInvalidNotificationType() throws Exception {

		String invalidRequest = VALID_REQUEST.replace("\"CONFIRMATION\"", "\"UNKNOWN_TYPE\"");

		mockMvc.perform(post(NOTIFICATIONS_URL).with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
				.content(invalidRequest)).andExpect(status().isBadRequest());
	}

	@Test
	void shouldRejectInvalidPaginationParameters() throws Exception {

		mockMvc.perform(get(NOTIFICATIONS_URL).with(adminJwt()).param("page", "-1")).andExpect(status().isBadRequest());

		mockMvc.perform(get(NOTIFICATIONS_URL).with(adminJwt()).param("size", "101"))
				.andExpect(status().isBadRequest());
	}

	// ---------------------------------------------------------
	// Authorization
	// ---------------------------------------------------------

	@Test
	void shouldRejectRequestsWithoutAuthentication() throws Exception {

		mockMvc.perform(post(NOTIFICATIONS_URL).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get(NOTIFICATIONS_URL)).andExpect(status().isUnauthorized());
	}

	@Test
	void shouldAllowAdminAndReceptionist() throws Exception {

		mockMvc.perform(
				post(NOTIFICATIONS_URL).with(adminJwt()).contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
				.andExpect(status().isCreated());

		mockMvc.perform(get(NOTIFICATIONS_URL).with(receptionistJwt())).andExpect(status().isOk());
	}

	@Test
	void shouldRejectDoctorAndPatientRoles() throws Exception {

		mockMvc.perform(post(NOTIFICATIONS_URL).with(jwtWithRole("DOCTOR")).contentType(MediaType.APPLICATION_JSON)
				.content(VALID_REQUEST)).andExpect(status().isForbidden());

		mockMvc.perform(get(NOTIFICATIONS_URL).with(jwtWithRole("PATIENT"))).andExpect(status().isForbidden());
	}

	// ---------------------------------------------------------
	// Test helpers
	// ---------------------------------------------------------

	private void createNotification(String idempotencyKey, String email) throws Exception {

		String request = VALID_REQUEST.replace("appointment-1001-confirmation-v1", idempotencyKey)
				.replace("patient@example.com", email);

		mockMvc.perform(
				post(NOTIFICATIONS_URL).with(adminJwt()).contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isCreated());
	}

	private long countByIdempotencyKey(String idempotencyKey) {

		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM notifications
				WHERE idempotency_key = ?
				""", Long.class, idempotencyKey);

		return count == null ? 0 : count;
	}

	private RequestPostProcessor adminJwt() {
		return jwtWithRole("ADMIN");
	}

	private RequestPostProcessor receptionistJwt() {
		return jwtWithRole("RECEPTIONIST");
	}

	private RequestPostProcessor jwtWithRole(String role) {

		return jwt().jwt(token -> token.claim("role", role).claim("userId", 100L))
				.authorities(new SimpleGrantedAuthority("ROLE_" + role));
	}
}