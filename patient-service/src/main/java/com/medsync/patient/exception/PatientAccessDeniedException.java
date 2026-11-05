package com.medsync.patient.exception;

public class PatientAccessDeniedException extends RuntimeException {

	public PatientAccessDeniedException(String message) {
		super(message);
	}
}