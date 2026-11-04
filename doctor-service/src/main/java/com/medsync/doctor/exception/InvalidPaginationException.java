package com.medsync.doctor.exception;

public class InvalidPaginationException extends RuntimeException {

	public InvalidPaginationException(String message) {
		super(message);
	}
}