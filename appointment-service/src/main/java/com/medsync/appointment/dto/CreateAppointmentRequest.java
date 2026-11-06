package com.medsync.appointment.dto;

import java.time.Instant;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAppointmentRequest(

		@NotNull(message = "Patient ID is required") Long patientId,

		@NotNull(message = "Doctor user ID is required") Long doctorAuthUserId,

		@NotNull(message = "Appointment start time is required") @Future(message = "Appointment must be scheduled in the future") Instant startAt,

		@NotNull(message = "Appointment end time is required") @Future(message = "Appointment end time must be in the future") Instant endAt,

		@NotBlank(message = "Appointment reason is required") @Size(max = 500, message = "Reason cannot exceed 500 characters") String reason

) {
}