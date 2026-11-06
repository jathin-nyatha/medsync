package com.medsync.notification.service;

import com.medsync.notification.dto.EmailMessage;

public interface EmailProvider {

	EmailDeliveryResult send(EmailMessage emailMessage);

	record EmailDeliveryResult(int statusCode, String providerMessageId) {
	}
}