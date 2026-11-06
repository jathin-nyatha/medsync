
package com.medsync.appointment.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medsync.appointment.dto.AppointmentNotificationPayload;
import com.medsync.appointment.entity.OutboxEvent;
import com.medsync.appointment.entity.OutboxEventType;
import com.medsync.appointment.exception.InvalidAppointmentRequestException;
import com.medsync.appointment.repository.OutboxEventRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class OutboxEventPublisher {

	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;

	public OutboxEventPublisher(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {

		this.outboxEventRepository = outboxEventRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public String publish(OutboxEventType eventType, Long appointmentId, AppointmentNotificationPayload payload) {

		if (eventType == null) {
			throw new InvalidAppointmentRequestException("Outbox event type is required.");
		}

		if (appointmentId == null || appointmentId <= 0) {
			throw new InvalidAppointmentRequestException("Appointment ID must be a positive number.");
		}

		if (payload == null) {
			throw new InvalidAppointmentRequestException("Notification payload is required.");
		}

		String eventId = UUID.randomUUID().toString();

		String payloadJson;

		try {
			payloadJson = objectMapper.writeValueAsString(payload);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Could not serialize appointment notification payload.", exception);
		}

		OutboxEvent event = new OutboxEvent(eventId, eventType, appointmentId, payloadJson);

		outboxEventRepository.save(event);

		return eventId;
	}
}
