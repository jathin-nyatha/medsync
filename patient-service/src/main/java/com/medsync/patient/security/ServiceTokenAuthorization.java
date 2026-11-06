package com.medsync.patient.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component("serviceTokenAuthorization")
public class ServiceTokenAuthorization {

	private static final String SERVICE_TOKEN_TYPE = "service";
	private static final String APPOINTMENT_SERVICE_CLIENT_ID = "appointment-service";
	private static final String REQUIRED_SCOPE = "patients:read";

	/**
	 * Restricts profile reads to the Appointment Service machine identity. A
	 * matching scope by itself is not sufficient.
	 */
	public boolean isAppointmentServiceWithScope(Jwt jwt) {

		if (jwt == null || !SERVICE_TOKEN_TYPE.equals(jwt.getClaimAsString("token_type"))
				|| !APPOINTMENT_SERVICE_CLIENT_ID.equals(jwt.getSubject())) {
			return false;
		}

		return jwt.getClaimAsStringList("scope") != null && jwt.getClaimAsStringList("scope").contains(REQUIRED_SCOPE);
	}
}