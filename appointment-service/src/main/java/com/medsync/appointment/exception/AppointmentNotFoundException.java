package com.medsync.appointment.exception;

public class AppointmentNotFoundException extends RuntimeException {

	public AppointmentNotFoundException(Long appointmentId) {
		super("Appointment not found with ID: " + appointmentId);
	}
}