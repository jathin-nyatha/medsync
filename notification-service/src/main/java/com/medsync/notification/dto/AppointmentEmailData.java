package com.medsync.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppointmentEmailData(

		@NotBlank(message = "Recipient email is required") @Email(message = "Recipient email must be valid") @Size(max = 254, message = "Recipient email cannot exceed 254 characters") String recipientEmail,

		@NotBlank(message = "Patient name is required") @Size(max = 150, message = "Patient name cannot exceed 150 characters") String patientName,

		@NotBlank(message = "Doctor name is required") @Size(max = 150, message = "Doctor name cannot exceed 150 characters") String doctorName,

		@NotBlank(message = "Appointment date and time are required") @Size(max = 100, message = "Appointment date and time cannot exceed 100 characters") String appointmentDateTime,

		@NotBlank(message = "Appointment reason is required") @Size(max = 500, message = "Appointment reason cannot exceed 500 characters") String reason,

		@NotBlank(message = "Appointment reference is required") @Size(max = 100, message = "Appointment reference cannot exceed 100 characters") String appointmentReference,

		@Size(max = 500, message = "Cancellation reason cannot exceed 500 characters") String cancellationReason

) {
}