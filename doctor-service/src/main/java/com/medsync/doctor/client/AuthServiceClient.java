
package com.medsync.doctor.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.medsync.doctor.dto.DoctorEligibilityResponse;

@Component
public class AuthServiceClient {

	private final RestClient restClient;

	public AuthServiceClient(RestClient.Builder restClientBuilder,
			@Value("${medsync.services.auth.base-url}") String authServiceBaseUrl) {

		this.restClient = restClientBuilder.baseUrl(authServiceBaseUrl).build();
	}

	public DoctorEligibilityResponse checkDoctorEligibility(Long authUserId, String authorizationHeader) {

		return restClient.get().uri("/api/internal/users/{userId}/doctor-eligibility", authUserId)
				.header(HttpHeaders.AUTHORIZATION, authorizationHeader).retrieve()
				.body(DoctorEligibilityResponse.class);
	}
}
