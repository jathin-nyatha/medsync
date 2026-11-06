package com.medsync.appointment.entity;

import java.util.Set;

public enum AppointmentStatus {

	SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW;

	/**
	 * Statuses that reserve a doctor's time slot.
	 */
	public static Set<AppointmentStatus> getScheduleBlockingStatuses() {
		return Set.of(SCHEDULED, CONFIRMED);
	}

	/**
	 * Statuses that represent an active doctor-patient assignment.
	 */
	public static Set<AppointmentStatus> getActiveAssignmentStatuses() {
		return Set.of(SCHEDULED, CONFIRMED);
	}
}