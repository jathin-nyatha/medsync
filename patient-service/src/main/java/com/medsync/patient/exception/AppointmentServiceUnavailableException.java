package com.medsync.patient.exception;

public class AppointmentServiceUnavailableException extends RuntimeException {

	public AppointmentServiceUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}