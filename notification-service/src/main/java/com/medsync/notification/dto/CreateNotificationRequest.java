package com.medsync.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateNotificationRequest(

		@NotBlank(message = "Idempotency key is required") @Size(min = 8, max = 100, message = "Idempotency key must contain between 8 and 100 characters") @Pattern(regexp = "^[A-Za-z0-9._:-]+$", message = "Idempotency key contains unsupported characters") String idempotencyKey,

		@NotNull(message = "Notification type is required") AppointmentEmailType type,

		@NotNull(message = "Appointment email data is required") @Valid AppointmentEmailData data

) {
}