package com.medsync.appointment.dto;

public record AppointmentNotificationContext(Long patientId, Long doctorAuthUserId, String recipientEmail,
		String patientName, String doctorName) {
}