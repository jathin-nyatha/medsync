package com.medsync.appointment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppointmentNotificationPayload(

		@NotBlank @Email @Size(max = 254) String recipientEmail,

		@NotBlank @Size(max = 150) String patientName,

		@NotBlank @Size(max = 150) String doctorName,

		@NotBlank @Size(max = 100) String appointmentDateTime,

		@NotBlank @Size(max = 500) String reason,

		@NotBlank @Size(max = 100) String appointmentReference,

		@Size(max = 500) String cancellationReason

) {
}