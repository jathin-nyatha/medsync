
package com.medsync.appointment.client;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.medsync.appointment.client.dto.ServiceTokenRequest;
import com.medsync.appointment.client.dto.ServiceTokenResponse;

@Component
public class ServiceTokenProvider {

	private static final Set<String> REQUIRED_SCOPES = Set.of("users:email:read", "notifications:create");

	private static final long MAX_REFRESH_SKEW_SECONDS = 30;

	private final RestClient restClient;
	private final String authServiceUrl;
	private final String clientId;
	private final String clientSecret;

	private String cachedAccessToken;
	private Instant tokenExpiresAt;

	public ServiceTokenProvider(RestClient medSyncRestClient,
			@Value("${medsync.clients.auth-service-url:http://localhost:8081}") String authServiceUrl,
			@Value("${medsync.security.appointment-service.client-id:appointment-service}") String clientId,
			@Value("${medsync.security.appointment-service.client-secret:}") String clientSecret) {

		this.restClient = medSyncRestClient;
		this.authServiceUrl = removeTrailingSlash(authServiceUrl);
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	/**
	 * Returns a valid Bearer authorization header. Synchronization prevents
	 * concurrent token refresh requests.
	 */
	public synchronized String getAuthorizationHeader() {

		if (isCachedTokenValid()) {
			return "Bearer " + cachedAccessToken;
		}

		acquireServiceToken();

		return "Bearer " + cachedAccessToken;
	}

	private boolean isCachedTokenValid() {

		if (cachedAccessToken == null || tokenExpiresAt == null) {
			return false;
		}

		long remainingSeconds = Duration.between(Instant.now(), tokenExpiresAt).getSeconds();

		long refreshSkewSeconds = Math.min(MAX_REFRESH_SKEW_SECONDS, Math.max(1, remainingSeconds / 10));

		return Instant.now().isBefore(tokenExpiresAt.minusSeconds(refreshSkewSeconds));
	}

	private void acquireServiceToken() {

		validateConfiguration();

		ServiceTokenRequest request = new ServiceTokenRequest(clientId, clientSecret);

		try {

			ServiceTokenResponse response = restClient.post().uri(authServiceUrl + "/api/auth/service-token")
					.body(request).retrieve().body(ServiceTokenResponse.class);

			validateTokenResponse(response);

			this.cachedAccessToken = response.accessToken();

			this.tokenExpiresAt = Instant.now().plusSeconds(response.expiresIn());

		} catch (RestClientException exception) {

			this.cachedAccessToken = null;
			this.tokenExpiresAt = null;

			throw new IllegalStateException("Unable to acquire Appointment Service token from Auth Service.",
					exception);
		}
	}

	private void validateTokenResponse(ServiceTokenResponse response) {

		if (response == null || response.accessToken() == null || response.accessToken().isBlank()
				|| response.tokenType() == null || !"Bearer".equalsIgnoreCase(response.tokenType())
				|| response.expiresIn() <= 0 || response.scope() == null || response.scope().isBlank()) {

			throw new IllegalStateException("Auth Service returned an invalid service-token response.");
		}

		Set<String> grantedScopes = Arrays.stream(response.scope().trim().split("\\s+")).collect(Collectors.toSet());

		if (!grantedScopes.containsAll(REQUIRED_SCOPES)) {

			throw new IllegalStateException("Auth Service did not grant all required Appointment Service scopes.");
		}
	}

	private void validateConfiguration() {

		if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {

			throw new IllegalStateException("Appointment Service client credentials are not configured.");
		}
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
