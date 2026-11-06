package com.medsync.appointment.client.dto;

public record CreateNotificationRequest(String idempotencyKey, NotificationType type, AppointmentEmailData data) {
}