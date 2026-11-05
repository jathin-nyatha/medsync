package com.medsync.patient.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.medsync.patient.dto.DoctorPatientAccessResponse;
import com.medsync.patient.exception.AppointmentServiceIntegrationException;
import com.medsync.patient.exception.AppointmentServiceUnavailableException;
import com.medsync.patient.exception.PatientAccessDeniedException;

@Service
public class PatientAccessService {

	private final RestClient restClient;

	public PatientAccessService(@Value("${services.appointment.base-url}") String appointmentServiceBaseUrl) {

		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

		requestFactory.setConnectTimeout(Duration.ofSeconds(3));
		requestFactory.setReadTimeout(Duration.ofSeconds(5));

		this.restClient = RestClient.builder().baseUrl(appointmentServiceBaseUrl).requestFactory(requestFactory)
				.build();
	}

	public void validateDoctorAccess(Long patientId, Long doctorAuthUserId, String authorizationHeader) {

		if (patientId == null || patientId <= 0 || doctorAuthUserId == null || doctorAuthUserId <= 0) {
			throw new IllegalArgumentException("Valid patient and doctor IDs are required");
		}

		if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
			throw new PatientAccessDeniedException("A valid doctor authorization token is required");
		}

		try {

			DoctorPatientAccessResponse response = restClient.get()
					.uri(uriBuilder -> uriBuilder.path("/api/internal/assignments/active")
							.queryParam("patientId", patientId).queryParam("doctorAuthUserId", doctorAuthUserId)
							.build())
					.header(HttpHeaders.AUTHORIZATION, authorizationHeader).retrieve()
					.body(DoctorPatientAccessResponse.class);

			if (response == null || !response.isActive()) {
				throw new PatientAccessDeniedException("Doctor does not have an active assignment with this patient");
			}

		} catch (PatientAccessDeniedException exception) {

			throw exception;

		} catch (RestClientResponseException exception) {

			HttpStatusCode status = exception.getStatusCode();

			if (status.value() == 401 || status.value() == 403) {
				throw new AppointmentServiceIntegrationException(
						"Appointment Service rejected the authorization request", exception);
			}

			throw new AppointmentServiceIntegrationException("Appointment Service returned an unexpected HTTP response",
					exception);

		} catch (ResourceAccessException exception) {

			throw new AppointmentServiceUnavailableException("Unable to communicate with Appointment Service",
					exception);
		}
	}
}