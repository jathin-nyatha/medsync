package com.medsync.appointment.dto;

import java.time.Instant;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

public record RescheduleAppointmentRequest(

		@NotNull(message = "New appointment start time is required") @Future(message = "Appointment must be rescheduled to a future time") Instant startAt,

		@NotNull(message = "New appointment end time is required") @Future(message = "Appointment end time must be in the future") Instant endAt

) {
}