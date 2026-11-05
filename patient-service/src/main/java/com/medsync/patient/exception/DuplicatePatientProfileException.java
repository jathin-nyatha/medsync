package com.medsync.patient.exception;

public class DuplicatePatientProfileException extends RuntimeException {

	public DuplicatePatientProfileException(String message) {
		super(message);
	}
}