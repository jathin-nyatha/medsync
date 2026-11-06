package com.medsync.appointment.exception;

public class InvalidAppointmentRequestException extends RuntimeException {

	public InvalidAppointmentRequestException(String message) {
		super(message);
	}
}