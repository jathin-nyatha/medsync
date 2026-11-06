
package com.medsync.appointment.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.medsync.appointment.client.dto.AuthUserEmailResponse;
import com.medsync.appointment.exception.AppointmentReferenceValidationException;

@Component
public class AuthUserEmailClient {

	private final RestClient restClient;
	private final ServiceTokenProvider serviceTokenProvider;
	private final String authServiceUrl;

	public AuthUserEmailClient(RestClient medSyncRestClient, ServiceTokenProvider serviceTokenProvider,
			@Value("${medsync.clients.auth-service-url:http://localhost:8081}") String authServiceUrl) {

		this.restClient = medSyncRestClient;
		this.serviceTokenProvider = serviceTokenProvider;
		this.authServiceUrl = removeTrailingSlash(authServiceUrl);
	}

	public AuthUserEmailResponse getUserEmail(Long userId) {

		if (userId == null || userId <= 0) {
			throw new IllegalArgumentException("Auth user ID must be a positive number.");
		}

		String authorizationHeader = serviceTokenProvider.getAuthorizationHeader();

		AuthUserEmailResponse response;

		try {

			response = restClient.get().uri(authServiceUrl + "/api/internal/users/{userId}/email", userId)
					.header("Authorization", authorizationHeader).retrieve().body(AuthUserEmailResponse.class);

		} catch (RestClientResponseException exception) {

			throw mapResponseException(exception);

		} catch (ResourceAccessException exception) {

			throw serviceUnavailable(exception);

		} catch (RestClientException exception) {

			throw new AppointmentReferenceValidationException(HttpStatus.BAD_GATEWAY,
					"Auth Service Communication Failed", "Could not process the response from Auth Service.");
		}

		validateResponse(userId, response);

		return response;
	}

	private void validateResponse(Long requestedUserId, AuthUserEmailResponse response) {

		if (response == null || response.userId() == null || !requestedUserId.equals(response.userId())
				|| response.email() == null || response.email().isBlank()) {

			throw new AppointmentReferenceValidationException(HttpStatus.BAD_GATEWAY, "Invalid Auth Service Response",
					"Auth Service returned an invalid user email response.");
		}

		if (!response.enabled()) {

			throw new AppointmentReferenceValidationException(HttpStatus.UNPROCESSABLE_ENTITY,
					"Disabled Patient Account", "Notifications cannot be scheduled for a disabled user account.");
		}
	}

	private AppointmentReferenceValidationException mapResponseException(RestClientResponseException exception) {

		HttpStatusCode statusCode = exception.getStatusCode();

		if (statusCode.value() == 404) {

			return new AppointmentReferenceValidationException(HttpStatus.BAD_GATEWAY, "Auth User Not Found",
					"Auth Service could not find the user associated with the patient profile.");
		}

		if (statusCode.value() == 401 || statusCode.value() == 403) {

			return new AppointmentReferenceValidationException(HttpStatus.BAD_GATEWAY,
					"Auth Service Authentication Failed",
					"Appointment Service was not authorized to retrieve the user email.");
		}

		if (statusCode.is5xxServerError()) {
			return serviceUnavailable(exception);
		}

		return new AppointmentReferenceValidationException(HttpStatus.BAD_GATEWAY, "Auth Service Request Failed",
				"Auth Service rejected the user email lookup request.");
	}

	private AppointmentReferenceValidationException serviceUnavailable(Exception exception) {

		return new AppointmentReferenceValidationException(HttpStatus.SERVICE_UNAVAILABLE, "Auth Service Unavailable",
				"Auth Service is unavailable. Please try again later.");
	}

	private String removeTrailingSlash(String url) {

		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("Auth Service URL must be configured.");
		}

		String normalizedUrl = url.trim();

		while (normalizedUrl.endsWith("/")) {
			normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
		}

		return normalizedUrl;
	}
}
