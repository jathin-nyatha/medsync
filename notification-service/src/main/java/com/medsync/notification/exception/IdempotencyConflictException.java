package com.medsync.notification.exception;

public class IdempotencyConflictException extends RuntimeException {

	public IdempotencyConflictException(String idempotencyKey) {
		super("Idempotency key has already been used with different notification data: " + idempotencyKey);
	}
}