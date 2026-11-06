package com.medsync.appointment.client.dto;

public record AppointmentEmailData(String recipientEmail, String patientName, String doctorName,
		String appointmentDateTime, String reason, String appointmentReference, String cancellationReason) {
}