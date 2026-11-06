package com.medsync.appointment.exception;

public class AppointmentConflictException extends RuntimeException {

	public AppointmentConflictException(String message) {
		super(message);
	}
}