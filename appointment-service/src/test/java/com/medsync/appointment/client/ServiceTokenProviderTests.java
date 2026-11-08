package com.medsync.appointment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class ServiceTokenProviderTests {

	private static final String AUTH_SERVICE_URL = "http://localhost:8081";

	private static final String TOKEN_ENDPOINT = AUTH_SERVICE_URL + "/api/auth/service-token";

	private static final String CLIENT_ID = "appointment-service";

	private static final String CLIENT_SECRET = "test-client-secret";

	private static final String ACCESS_TOKEN = "test-access-token";

	private static final String REQUIRED_SCOPES = "users:email:read notifications:create patients:read doctors:read";

	private RestClient restClient;

	private MockRestServiceServer mockServer;

	private ServiceTokenProvider serviceTokenProvider;

	@BeforeEach
	void setUp() {

		RestClient.Builder builder = RestClient.builder();

		mockServer = MockRestServiceServer.bindTo(builder).build();

		restClient = builder.build();

		serviceTokenProvider = new ServiceTokenProvider(restClient, AUTH_SERVICE_URL, CLIENT_ID, CLIENT_SECRET);
	}

	@Test
	void getAuthorizationHeaderAcquiresServiceTokenSuccessfully() {

		mockServer.expect(requestTo(TOKEN_ENDPOINT)).andExpect(method(HttpMethod.POST))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.clientId").value(CLIENT_ID))
				.andExpect(jsonPath("$.clientSecret").value(CLIENT_SECRET))
				.andRespond(withSuccess(validTokenResponse(), MediaType.APPLICATION_JSON));

		String authorizationHeader = serviceTokenProvider.getAuthorizationHeader();

		assertThat(authorizationHeader).isEqualTo("Bearer " + ACCESS_TOKEN);

		mockServer.verify();
	}

	@Test
	void getAuthorizationHeaderReusesCachedToken() {

		mockServer.expect(requestTo(TOKEN_ENDPOINT)).andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(validTokenResponse(), MediaType.APPLICATION_JSON));

		String firstHeader = serviceTokenProvider.getAuthorizationHeader();

		String secondHeader = serviceTokenProvider.getAuthorizationHeader();

		assertThat(firstHeader).isEqualTo("Bearer " + ACCESS_TOKEN);
		assertThat(secondHeader).isEqualTo(firstHeader);

		mockServer.verify();
	}

	@Test
	void getAuthorizationHeaderRejectsMissingClientId() {

		ServiceTokenProvider provider = new ServiceTokenProvider(restClient, AUTH_SERVICE_URL, " ", CLIENT_SECRET);

		assertThatThrownBy(provider::getAuthorizationHeader).isInstanceOf(IllegalStateException.class)
				.hasMessage("Appointment Service client credentials are not configured.");

		mockServer.verify();
	}

	@Test
	void getAuthorizationHeaderRejectsMissingClientSecret() {

		ServiceTokenProvider provider = new ServiceTokenProvider(restClient, AUTH_SERVICE_URL, CLIENT_ID, " ");

		assertThatThrownBy(provider::getAuthorizationHeader).isInstanceOf(IllegalStateException.class)
				.hasMessage("Appointment Service client credentials are not configured.");

		mockServer.verify();
	}

	@Test
	void getAuthorizationHeaderRejectsInvalidTokenResponse() {

		String invalidResponse = """
				{
				    "accessToken": "",
				    "tokenType": "Bearer",
				    "expiresIn": 3600,
				    "scope": "%s"
				}
				""".formatted(REQUIRED_SCOPES);

		mockServer.expect(requestTo(TOKEN_ENDPOINT))
				.andRespond(withSuccess(invalidResponse, MediaType.APPLICATION_JSON));

		assertThatThrownBy(serviceTokenProvider::getAuthorizationHeader).isInstanceOf(IllegalStateException.class)
				.hasMessage("Auth Service returned an invalid service-token response.");

		mockServer.verify();
	}

	@Test
	void getAuthorizationHeaderRejectsMissingRequiredScopes() {

		String insufficientScopesResponse = """
				{
				    "accessToken": "%s",
				    "tokenType": "Bearer",
				    "expiresIn": 3600,
				    "scope": "users:email:read notifications:create patients:read"
				}
				""".formatted(ACCESS_TOKEN);

		mockServer.expect(requestTo(TOKEN_ENDPOINT))
				.andRespond(withSuccess(insufficientScopesResponse, MediaType.APPLICATION_JSON));

		assertThatThrownBy(serviceTokenProvider::getAuthorizationHeader).isInstanceOf(IllegalStateException.class)
				.hasMessage("Auth Service did not grant all required Appointment Service scopes.");

		mockServer.verify();
	}

	@Test
	void getAuthorizationHeaderRefreshesExpiredToken() {

		ReflectionTestUtils.setField(serviceTokenProvider, "cachedAccessToken", "expired-access-token");

		ReflectionTestUtils.setField(serviceTokenProvider, "tokenExpiresAt", Instant.now().minusSeconds(60));

		mockServer.expect(requestTo(TOKEN_ENDPOINT)).andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(validTokenResponse(), MediaType.APPLICATION_JSON));

		String authorizationHeader = serviceTokenProvider.getAuthorizationHeader();

		assertThat(authorizationHeader).isEqualTo("Bearer " + ACCESS_TOKEN);

		mockServer.verify();
	}

	@Test
	void getAuthorizationHeaderWrapsNetworkFailure() {
		mockServer.expect(requestTo(TOKEN_ENDPOINT))
				.andRespond(withException(new IOException("Auth Service connection timed out.")));

		assertThatThrownBy(serviceTokenProvider::getAuthorizationHeader).isInstanceOf(IllegalStateException.class)
				.hasMessage("Unable to acquire Appointment Service token from Auth Service.")
				.hasCauseInstanceOf(ResourceAccessException.class);

		mockServer.verify();
	}

	@Test
	void getAuthorizationHeaderRejectsNonBearerTokenType() {

		String invalidResponse = """
				{
				    "accessToken": "%s",
				    "tokenType": "Basic",
				    "expiresIn": 3600,
				    "scope": "%s"
				}
				""".formatted(ACCESS_TOKEN, REQUIRED_SCOPES);

		mockServer.expect(requestTo(TOKEN_ENDPOINT))
				.andRespond(withSuccess(invalidResponse, MediaType.APPLICATION_JSON));

		assertThatThrownBy(serviceTokenProvider::getAuthorizationHeader).isInstanceOf(IllegalStateException.class)
				.hasMessage("Auth Service returned an invalid service-token response.");

		mockServer.verify();
	}

	@Test
	void getAuthorizationHeaderRejectsNonPositiveTokenExpiration() {

		String invalidResponse = """
				{
				    "accessToken": "%s",
				    "tokenType": "Bearer",
				    "expiresIn": 0,
				    "scope": "%s"
				}
				""".formatted(ACCESS_TOKEN, REQUIRED_SCOPES);

		mockServer.expect(requestTo(TOKEN_ENDPOINT))
				.andRespond(withSuccess(invalidResponse, MediaType.APPLICATION_JSON));

		assertThatThrownBy(serviceTokenProvider::getAuthorizationHeader).isInstanceOf(IllegalStateException.class)
				.hasMessage("Auth Service returned an invalid service-token response.");

		mockServer.verify();
	}

	@Test
	void getAuthorizationHeaderWrapsAuthServiceHttpFailure() {

		mockServer.expect(requestTo(TOKEN_ENDPOINT)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatThrownBy(serviceTokenProvider::getAuthorizationHeader).isInstanceOf(IllegalStateException.class)
				.hasMessage("Unable to acquire Appointment Service token from Auth Service.")
				.hasCauseInstanceOf(RestClientResponseException.class);

		mockServer.verify();
	}

	private String validTokenResponse() {

		return """
				{
				    "accessToken": "%s",
				    "tokenType": "Bearer",
				    "expiresIn": 3600,
				    "scope": "%s"
				}
				""".formatted(ACCESS_TOKEN, REQUIRED_SCOPES);
	}
}