package com.medsync.appointment.dto;

import java.time.Instant;

import com.medsync.appointment.entity.AppointmentStatus;

public record AppointmentResponse(

		Long id,

		Long patientId,

		Long doctorAuthUserId,

		Long createdByAuthUserId,

		Instant startAt,

		Instant endAt,

		String reason,

		AppointmentStatus status,

		String cancellationReason,

		Instant cancelledAt,

		Instant createdAt,

		Instant updatedAt

) {
}