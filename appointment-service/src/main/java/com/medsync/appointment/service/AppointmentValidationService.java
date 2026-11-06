package com.medsync.appointment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.medsync.appointment.client.AuthUserEmailClient;
import com.medsync.appointment.client.ServiceTokenProvider;
import com.medsync.appointment.client.dto.AuthUserEmailResponse;
import com.medsync.appointment.client.dto.DoctorValidationResponse;
import com.medsync.appointment.client.dto.PatientValidationResponse;
import com.medsync.appointment.dto.AppointmentNotificationContext;
import com.medsync.appointment.exception.AppointmentReferenceValidationException;

@Service
public class AppointmentValidationService {

	private final RestClient restClient;
	private final ServiceTokenProvider serviceTokenProvider;
	private final AuthUserEmailClient authUserEmailClient;
	private final String doctorServiceUrl;
	private final String patientServiceUrl;

	public AppointmentValidationService(RestClient medSyncRestClient, ServiceTokenProvider serviceTokenProvider,
			AuthUserEmailClient authUserEmailClient,
			@Value("${medsync.clients.doctor-service-url}") String doctorServiceUrl,
			@Value("${medsync.clients.patient-service-url}") String patientServiceUrl) {

		this.restClient = medSyncRestClient;
		this.serviceTokenProvider = serviceTokenProvider;
		this.authUserEmailClient = authUserEmailClient;
		this.doctorServiceUrl = removeTrailingSlash(doctorServiceUrl);
		this.patientServiceUrl = removeTrailingSlash(patientServiceUrl);
	}

	/**
	 * Resolves all participant information before the appointment transaction
	 * starts. No downstream HTTP calls should be made from AppointmentService.
	 */
	public AppointmentNotificationContext resolveNotificationContext(Long patientId, Long doctorAuthUserId) {

		validatePositiveId(patientId, "Patient ID");
		validatePositiveId(doctorAuthUserId, "Doctor user ID");

		String authorizationHeader = serviceTokenProvider.getAuthorizationHeader();

		PatientValidationResponse patient = getPatient(patientId, authorizationHeader);

		DoctorValidationResponse doctor = getDoctor(doctorAuthUserId, authorizationHeader);

		AuthUserEmailResponse emailResponse = authUserEmailClient.getUserEmail(patient.authUserId());

		return new AppointmentNotificationContext(patientId, doctorAuthUserId, emailResponse.email().trim(),
				buildFullName(patient.firstName(), patient.lastName(), "Patient"),
				buildFullName(doctor.firstName(), doctor.lastName(), "Doctor"));
	}

	private PatientValidationResponse getPatient(Long patientId, String authorizationHeader) {

		PatientValidationResponse patient = executeRequest(
				() -> restClient.get().uri(patientServiceUrl + "/api/patients/{patientId}", patientId)
						.header(HttpHeaders.AUTHORIZATION, authorizationHeader).retrieve()
						.body(PatientValidationResponse.class),
				"Patient Service");

		if (patient == null || patient.id() == null || !patientId.equals(patient.id()) || patient.authUserId() == null
				|| patient.authUserId() <= 0) {

			throw invalidResponse("Invalid Patient Service Response",
					"Patient Service returned an invalid patient profile.");
		}

		return patient;
	}

	private DoctorValidationResponse getDoctor(Long doctorAuthUserId, String authorizationHeader) {

		DoctorValidationResponse doctor = executeRequest(() -> restClient.get()
				.uri(doctorServiceUrl + "/api/doctors/by-auth-user/{authUserId}", doctorAuthUserId)
				.header(HttpHeaders.AUTHORIZATION, authorizationHeader).retrieve().body(DoctorValidationResponse.class),
				"Doctor Service");

		if (doctor == null || doctor.id() == null || doctor.authUserId() == null
				|| !doctorAuthUserId.equals(doctor.authUserId()) || doctor.status() == null) {

			throw invalidResponse("Invalid Doctor Service Response",
					"Doctor Service returned an invalid doctor profile.");
		}

		if (!"ACTIVE".equalsIgnoreCase(doctor.status())) {

			throw new AppointmentReferenceValidationException(HttpStatus.UNPROCESSABLE_ENTITY, "Inactive Doctor",
					"Appointments cannot be scheduled with an inactive doctor.");
		}

		return doctor;
	}

	private String buildFullName(String firstName, String lastName, String participantType) {

		String first = firstName == null ? "" : firstName.trim();
		String last = lastName == null ? "" : lastName.trim();

		String fullName = (first + " " + last).trim();

		if (fullName.isBlank() || fullName.length() > 150) {

			throw invalidResponse("Invalid " + participantType + " Name",
					participantType + " profile contains an invalid name.");
		}

		return fullName;
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
						serviceName + " rejected Appointment Service authentication.");
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

	private AppointmentReferenceValidationException invalidResponse(String title, String message) {

		return new AppointmentReferenceValidationException(HttpStatus.BAD_GATEWAY, title, message);
	}

	private void validatePositiveId(Long id, String fieldName) {

		if (id == null || id <= 0) {

			throw new AppointmentReferenceValidationException(HttpStatus.BAD_REQUEST, "Invalid Reference",
					fieldName + " must be a positive number.");
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