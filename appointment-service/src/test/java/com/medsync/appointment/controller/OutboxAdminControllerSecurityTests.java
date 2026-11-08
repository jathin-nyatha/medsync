
package com.medsync.appointment.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.entity.OutboxEventType;
import com.medsync.appointment.repository.OutboxEventRepository;

@SpringBootTest
@AutoConfigureMockMvc
class OutboxAdminControllerSecurityTests {

	private static final String TEST_PREFIX = "it-outbox-admin-" + UUID.randomUUID() + "-";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("DELETE FROM outbox_events WHERE event_id LIKE ?", TEST_PREFIX + "%");
	}

	@Test
	void adminCanReplayFailedEvent() throws Exception {

		String eventId = createFailedEvent("admin-replay");

		mockMvc.perform(post("/api/internal/outbox/events/{eventId}/replay", eventId)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isAccepted());
	}

	@Test
	void receptionistCannotReplayFailedEvent() throws Exception {

		String eventId = createFailedEvent("receptionist-replay");

		mockMvc.perform(post("/api/internal/outbox/events/{eventId}/replay", eventId)
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedUserCannotReplayFailedEvent() throws Exception {

		String eventId = createFailedEvent("anonymous-replay");

		mockMvc.perform(post("/api/internal/outbox/events/{eventId}/replay", eventId))
				.andExpect(status().isUnauthorized());
	}

	private String createFailedEvent(String suffix) {

		String payload = """
				{
				  "recipientEmail": "aarav.sharma@example.com",
				  "patientName": "Aarav Sharma",
				  "doctorName": "Dr. Meera Nair",
				  "appointmentDateTime": "2026-11-10T10:30:00Z",
				  "reason": "Annual health consultation",
				  "appointmentReference": "APPT-987654321",
				  "cancellationReason": null
				}
				""";

		OutboxEvent event = new OutboxEvent(TEST_PREFIX + suffix, OutboxEventType.APPOINTMENT_CONFIRMATION, 987654321L,
				payload);

		event.setNextAttemptAt(Instant.now().minusSeconds(5));

		OutboxEvent saved = outboxEventRepository.saveAndFlush(event);

		jdbcTemplate.update("""
				UPDATE outbox_events
				SET
				    status = 'FAILED',
				    attempts = 5,
				    last_error = 'Permanent failure'
				WHERE event_id = ?
				""", saved.getEventId());

		return saved.getEventId();
	}
}
