package com.medsync.appointment.exception;

public class InvalidAppointmentTimeException extends RuntimeException {

	public InvalidAppointmentTimeException(String message) {
		super(message);
	}
}