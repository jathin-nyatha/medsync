package com.medsync.appointment.service;

import org.springframework.stereotype.Component;

import com.medsync.appointment.dto.AppointmentResponse;
import com.medsync.appointment.entity.Appointment;

@Component
public class AppointmentMapper {

	public AppointmentResponse toResponse(Appointment appointment) {

		return new AppointmentResponse(appointment.getId(), appointment.getPatientId(),
				appointment.getDoctorAuthUserId(), appointment.getCreatedByAuthUserId(), appointment.getStartAt(),
				appointment.getEndAt(), appointment.getReason(), appointment.getStatus(),
				appointment.getCancellationReason(), appointment.getCancelledAt(), appointment.getCreatedAt(),
				appointment.getUpdatedAt());
	}
}