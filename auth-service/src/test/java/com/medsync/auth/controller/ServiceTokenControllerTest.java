
package com.medsync.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.medsync.auth.dto.ServiceTokenRequest;
import com.medsync.auth.dto.ServiceTokenResponse;
import com.medsync.auth.security.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

class ServiceTokenControllerTest {

	private static KeyPair keyPair;

	private JwtService jwtService;

	private ServiceTokenController controller;

	private static final String CLIENT_ID = "appointment-service";

	private static final String CLIENT_SECRET = "test-client-secret";

	@BeforeAll
	static void generateRsaKeyPair() throws Exception {

		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");

		generator.initialize(2048);

		keyPair = generator.generateKeyPair();
	}

	@BeforeEach
	void setUp() {

		jwtService = new JwtService(keyPair.getPrivate(), "medsync-auth-service", 3600000L, 300L);

		controller = new ServiceTokenController(jwtService, CLIENT_ID, CLIENT_SECRET);
	}

	@Test
	void shouldIssueServiceTokenForValidCredentials() {

		ServiceTokenRequest request = new ServiceTokenRequest(CLIENT_ID, CLIENT_SECRET);

		ResponseEntity<ServiceTokenResponse> response = controller.issueServiceToken(request);

		assertEquals(HttpStatus.OK, response.getStatusCode());

		assertNotNull(response.getBody());

		assertNotNull(response.getBody().accessToken());

		assertEquals("Bearer", response.getBody().tokenType());

		assertEquals(300L, response.getBody().expiresIn());

		assertEquals("notifications:create users:email:read", response.getBody().scope());
	}

	@Test
	void shouldGenerateJwtWithExpectedClaimsAndValidRsaSignature() {

		ServiceTokenRequest request = new ServiceTokenRequest(CLIENT_ID, CLIENT_SECRET);

		ResponseEntity<ServiceTokenResponse> response = controller.issueServiceToken(request);

		assertNotNull(response.getBody());

		String accessToken = response.getBody().accessToken();

		// Parsing verifies the JWT signature using the public key.
		Claims claims = Jwts.parser().verifyWith((RSAPublicKey) keyPair.getPublic()).build()
				.parseSignedClaims(accessToken).getPayload();

		assertEquals("appointment-service", claims.getSubject());

		assertEquals("medsync-auth-service", claims.getIssuer());

		assertEquals("service", claims.get("token_type", String.class));

		@SuppressWarnings("unchecked")
		List<String> scopes = (List<String>) claims.get("scope");

		assertEquals(List.of("notifications:create", "users:email:read"), scopes);

		assertNotNull(claims.getIssuedAt());

		assertNotNull(claims.getExpiration());

		assertTrue(claims.getExpiration().after(claims.getIssuedAt()), "Expiration must be after issued-at time");

		long lifetimeSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;

		assertEquals(300L, lifetimeSeconds);
	}

	@Test
	void shouldRejectInvalidClientId() {

		ServiceTokenRequest request = new ServiceTokenRequest("invalid-client", CLIENT_SECRET);

		ResponseEntity<ServiceTokenResponse> response = controller.issueServiceToken(request);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

		assertEquals(null, response.getBody());
	}

	@Test
	void shouldRejectInvalidClientSecret() {

		ServiceTokenRequest request = new ServiceTokenRequest(CLIENT_ID, "incorrect-secret");

		ResponseEntity<ServiceTokenResponse> response = controller.issueServiceToken(request);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

		assertEquals(null, response.getBody());
	}

	@Test
	void shouldRejectRequestWhenServerCredentialsAreNotConfigured() {

		ServiceTokenController unconfiguredController = new ServiceTokenController(jwtService, "", "");

		ServiceTokenRequest request = new ServiceTokenRequest(CLIENT_ID, CLIENT_SECRET);

		ResponseEntity<ServiceTokenResponse> response = unconfiguredController.issueServiceToken(request);

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());

		assertEquals(null, response.getBody());
	}
}
