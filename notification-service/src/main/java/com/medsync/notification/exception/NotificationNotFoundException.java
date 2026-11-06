package com.medsync.notification.exception;

public class NotificationNotFoundException extends RuntimeException {

	public NotificationNotFoundException(Long notificationId) {
		super("Notification not found with ID: " + notificationId);
	}
}