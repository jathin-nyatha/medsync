package com.medsync.doctor.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.medsync.doctor.dto.DoctorEligibilityResponse;

@Component
public class AuthServiceClient {

	private final RestClient restClient;

	public AuthServiceClient(@Value("${medsync.services.auth.base-url}") String authServiceBaseUrl) {

		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

		requestFactory.setConnectTimeout(Duration.ofSeconds(3));
		requestFactory.setReadTimeout(Duration.ofSeconds(5));

		this.restClient = RestClient.builder().baseUrl(authServiceBaseUrl).requestFactory(requestFactory).build();
	}

	public DoctorEligibilityResponse checkDoctorEligibility(Long authUserId, String authorizationHeader) {

		return restClient.get().uri("/api/internal/users/{userId}/doctor-eligibility", authUserId)
				.header(HttpHeaders.AUTHORIZATION, authorizationHeader).retrieve()
				.body(DoctorEligibilityResponse.class);
	}
}