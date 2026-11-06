package com.medsync.auth.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medsync.auth.dto.ServiceTokenRequest;
import com.medsync.auth.dto.ServiceTokenResponse;
import com.medsync.auth.security.JwtService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class ServiceTokenController {

	private static final String APPOINTMENT_SERVICE_CLIENT_ID = "appointment-service";

	private static final List<String> APPOINTMENT_SERVICE_SCOPES = List.of("notifications:create", "users:email:read");

	private final JwtService jwtService;
	private final String configuredClientId;
	private final String configuredClientSecret;

	public ServiceTokenController(JwtService jwtService,
			@Value("${medsync.security.appointment-service.client-id:}") String configuredClientId,
			@Value("${medsync.security.appointment-service.client-secret:}") String configuredClientSecret) {

		this.jwtService = jwtService;
		this.configuredClientId = configuredClientId;
		this.configuredClientSecret = configuredClientSecret;
	}

	@PostMapping("/service-token")
	public ResponseEntity<ServiceTokenResponse> issueServiceToken(@Valid @RequestBody ServiceTokenRequest request) {

		if (configuredClientId == null || configuredClientId.isBlank() || configuredClientSecret == null
				|| configuredClientSecret.isBlank()) {

			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
		}

		boolean validClientId = MessageDigest.isEqual(configuredClientId.getBytes(StandardCharsets.UTF_8),
				request.clientId().getBytes(StandardCharsets.UTF_8));

		boolean validClientSecret = MessageDigest.isEqual(configuredClientSecret.getBytes(StandardCharsets.UTF_8),
				request.clientSecret().getBytes(StandardCharsets.UTF_8));

		if (!(validClientId & validClientSecret)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		String accessToken = jwtService.generateServiceToken(APPOINTMENT_SERVICE_CLIENT_ID, APPOINTMENT_SERVICE_SCOPES);

		ServiceTokenResponse response = new ServiceTokenResponse(accessToken, "Bearer",
				jwtService.getServiceTokenExpirationSeconds(), String.join(" ", APPOINTMENT_SERVICE_SCOPES));

		return ResponseEntity.ok(response);
	}
}