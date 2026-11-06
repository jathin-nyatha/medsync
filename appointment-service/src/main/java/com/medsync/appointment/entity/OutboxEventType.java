package com.medsync.appointment.entity;

public enum OutboxEventType {

	APPOINTMENT_CONFIRMATION,

	APPOINTMENT_RESCHEDULED,

	APPOINTMENT_CANCELLED
}