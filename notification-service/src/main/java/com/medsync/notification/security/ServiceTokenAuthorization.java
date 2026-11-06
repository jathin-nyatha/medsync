package com.medsync.notification.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component("serviceTokenAuthorization")
public class ServiceTokenAuthorization {

	private static final String SERVICE_TOKEN_TYPE = "service";
	private static final String APPOINTMENT_SERVICE_CLIENT_ID = "appointment-service";

	public boolean isValidNotificationServiceToken(Jwt jwt) {

		return jwt != null && SERVICE_TOKEN_TYPE.equals(jwt.getClaimAsString("token_type"))
				&& APPOINTMENT_SERVICE_CLIENT_ID.equals(jwt.getSubject());
	}
}