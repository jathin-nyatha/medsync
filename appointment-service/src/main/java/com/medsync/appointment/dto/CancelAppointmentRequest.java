package com.medsync.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelAppointmentRequest(

		@NotBlank(message = "Cancellation reason is required") @Size(max = 500, message = "Cancellation reason cannot exceed 500 characters") String cancellationReason

) {
}