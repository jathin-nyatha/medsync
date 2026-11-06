
package com.medsync.appointment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.medsync.appointment.client.dto.DoctorValidationResponse;
import com.medsync.appointment.client.dto.PatientValidationResponse;
import com.medsync.appointment.dto.CreateAppointmentRequest;
import com.medsync.appointment.exception.AppointmentReferenceValidationException;

@Service
public class AppointmentValidationService {

	private final RestClient restClient;
	private final String doctorServiceUrl;
	private final String patientServiceUrl;

	public AppointmentValidationService(RestClient medSyncRestClient,
			@Value("${medsync.clients.doctor-service-url}") String doctorServiceUrl,
			@Value("${medsync.clients.patient-service-url}") String patientServiceUrl) {

		this.restClient = medSyncRestClient;
		this.doctorServiceUrl = removeTrailingSlash(doctorServiceUrl);
		this.patientServiceUrl = removeTrailingSlash(patientServiceUrl);
	}

	public void validateAppointmentReferences(CreateAppointmentRequest request, String authorizationHeader) {

		validateAuthorizationHeader(authorizationHeader);

		validatePatient(request.patientId(), authorizationHeader);

		validateDoctor(request.doctorAuthUserId(), authorizationHeader);
	}

	private void validatePatient(Long patientId, String authorizationHeader) {

		PatientValidationResponse patient = executeRequest(
				() -> restClient.get().uri(patientServiceUrl + "/api/patients/{patientId}", patientId)
						.header(HttpHeaders.AUTHORIZATION, authorizationHeader).retrieve()
						.body(PatientValidationResponse.class),
				"Patient Service");

		if (patient == null || patient.id() == null || !patientId.equals(patient.id())) {

			throw new AppointmentReferenceValidationException(HttpStatus.BAD_GATEWAY,
					"Invalid Patient Service Response",
					"Patient Service returned an invalid patient profile response.");
		}
	}

	private void validateDoctor(Long doctorAuthUserId, String authorizationHeader) {

		DoctorValidationResponse doctor = executeRequest(() -> restClient.get()
				.uri(doctorServiceUrl + "/api/doctors/by-auth-user/{authUserId}", doctorAuthUserId)
				.header(HttpHeaders.AUTHORIZATION, authorizationHeader).retrieve().body(DoctorValidationResponse.class),
				"Doctor Service");

		if (doctor == null || doctor.authUserId() == null || !doctorAuthUserId.equals(doctor.authUserId())
				|| doctor.status() == null) {

			throw new AppointmentReferenceValidationException(HttpStatus.BAD_GATEWAY, "Invalid Doctor Service Response",
					"Doctor Service returned an invalid doctor profile response.");
		}

		if (!"ACTIVE".equalsIgnoreCase(doctor.status())) {

			throw new AppointmentReferenceValidationException(HttpStatus.UNPROCESSABLE_ENTITY, "Inactive Doctor",
					"Appointments cannot be scheduled with an inactive doctor.");
		}
	}

	private <T> T executeRequest(RemoteRequest<T> request, String serviceName) {

		try {

			return request.execute();

		} catch (RestClientResponseException exception) {

			int statusCode = exception.getStatusCode().value();

			if (statusCode == 404) {

				throw new AppointmentReferenceValidationException(HttpStatus.NOT_FOUND, "Referenced Resource Not Found",
						serviceName + " could not find the requested profile.");
			}

			if (statusCode == 401 || statusCode == 403) {

				throw new AppointmentReferenceValidationException(HttpStatus.BAD_GATEWAY,
						"Downstream Authentication Failed",
						serviceName + " rejected the forwarded authentication token.");
			}

			if (statusCode >= 500 && statusCode <= 599) {

				throw serviceUnavailable(serviceName);
			}

			throw new AppointmentReferenceValidationException(HttpStatus.BAD_GATEWAY, "Downstream Request Failed",
					serviceName + " rejected the profile validation request.");

		} catch (ResourceAccessException exception) {

			throw serviceUnavailable(serviceName);

		} catch (RestClientException exception) {

			throw new AppointmentReferenceValidationException(HttpStatus.BAD_GATEWAY, "Downstream Communication Failed",
					"Could not process the response from " + serviceName + ".");
		}
	}

	private AppointmentReferenceValidationException serviceUnavailable(String serviceName) {

		return new AppointmentReferenceValidationException(HttpStatus.SERVICE_UNAVAILABLE,
				"Dependent Service Unavailable", serviceName + " is unavailable. Please try again later.");
	}

	private void validateAuthorizationHeader(String authorizationHeader) {

		if (authorizationHeader == null || authorizationHeader.isBlank()
				|| !authorizationHeader.startsWith("Bearer ")) {

			throw new AppointmentReferenceValidationException(HttpStatus.UNAUTHORIZED, "Missing Authentication Token",
					"A valid Bearer token is required for profile validation.");
		}
	}

	private String removeTrailingSlash(String url) {

		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("A downstream service URL must be configured.");
		}

		String normalizedUrl = url.trim();

		while (normalizedUrl.endsWith("/")) {
			normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
		}

		return normalizedUrl;
	}

	@FunctionalInterface
	private interface RemoteRequest<T> {
		T execute();
	}
}
