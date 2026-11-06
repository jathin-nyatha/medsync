
package com.medsync.auth.security;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medsync.auth.config.JwtAuthenticationConfig;
import com.medsync.auth.config.SecurityConfig;
import com.medsync.auth.controller.InternalUserController;
import com.medsync.auth.dto.UserEmailResponse;
import com.medsync.auth.service.UserService;

import io.jsonwebtoken.Jwts;

@WebMvcTest(controllers = InternalUserController.class)
@Import({ SecurityConfig.class, JwtAuthenticationConfig.class,
		InternalUserSecurityIntegrationTest.TestJwtConfiguration.class })
class InternalUserSecurityIntegrationTest {

	private static final String ISSUER = "medsync-auth-service";

	private static final Long USER_ID = 42L;

	private static final String EMAIL = "doctor@medsync.com";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestJwtKeys testJwtKeys;

	@MockitoBean
	private UserService userService;

	// Required by the AuthenticationManager bean in SecurityConfig.
	@MockitoBean
	private UserDetailsService userDetailsService;

	@MockitoBean
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {

		when(userService.getUserEmail(USER_ID)).thenReturn(new UserEmailResponse(USER_ID, EMAIL, true));
	}

	@Test
	void adminShouldAccessEmailLookup() throws Exception {

		mockMvc.perform(get(emailLookupUrl()).header("Authorization",
				bearerToken("admin@medsync.com", "ADMIN", null, null, null, testJwtKeys.keyPair())))
				.andExpect(status().isOk());

		verify(userService).getUserEmail(USER_ID);
	}

	@Test
	void receptionistShouldAccessEmailLookup() throws Exception {

		mockMvc.perform(get(emailLookupUrl()).header("Authorization",
				bearerToken("receptionist@medsync.com", "RECEPTIONIST", null, null, null, testJwtKeys.keyPair())))
				.andExpect(status().isOk());

		verify(userService).getUserEmail(USER_ID);
	}

	@Test
	void appointmentServiceWithEmailScopeShouldAccessEmailLookup() throws Exception {

		mockMvc.perform(get(emailLookupUrl()).header("Authorization", bearerToken("appointment-service", null,
				List.of("users:email:read"), null, null, testJwtKeys.keyPair()))).andExpect(status().isOk());

		verify(userService).getUserEmail(USER_ID);
	}

	@Test
	void appointmentServiceWithoutEmailScopeShouldBeForbidden() throws Exception {

		mockMvc.perform(get(emailLookupUrl()).header("Authorization", bearerToken("appointment-service", null,
				List.of("notifications:create"), null, null, testJwtKeys.keyPair()))).andExpect(status().isForbidden());

		verify(userService, never()).getUserEmail(USER_ID);
	}

	@Test
	void doctorShouldNotAccessEmailLookup() throws Exception {

		mockMvc.perform(get(emailLookupUrl()).header("Authorization",
				bearerToken("doctor@medsync.com", "DOCTOR", null, null, null, testJwtKeys.keyPair())))
				.andExpect(status().isForbidden());

		verify(userService, never()).getUserEmail(USER_ID);
	}

	@Test
	void appointmentServiceShouldNotAccessOtherAdminOnlyInternalEndpoints() throws Exception {

		mockMvc.perform(get("/api/internal/users/{userId}/doctor-eligibility", USER_ID).header("Authorization",
				bearerToken("appointment-service", null, List.of("notifications:create", "users:email:read"), null,
						null, testJwtKeys.keyPair())))
				.andExpect(status().isForbidden());

		verify(userService, never()).checkDoctorEligibility(USER_ID);
	}

	@Test
	void requestWithoutTokenShouldBeUnauthorized() throws Exception {

		mockMvc.perform(get(emailLookupUrl())).andExpect(status().isUnauthorized());

		verify(userService, never()).getUserEmail(USER_ID);
	}

	@Test
	void tokenSignedWithIncorrectKeyShouldBeUnauthorized() throws Exception {

		KeyPair incorrectKeyPair = generateKeyPair();

		mockMvc.perform(get(emailLookupUrl()).header("Authorization",
				bearerToken("appointment-service", null, List.of("users:email:read"), null, null, incorrectKeyPair)))
				.andExpect(status().isUnauthorized());

		verify(userService, never()).getUserEmail(USER_ID);
	}

	@Test
	void expiredTokenShouldBeUnauthorized() throws Exception {

		Instant now = Instant.now();

		mockMvc.perform(get(emailLookupUrl()).header("Authorization", bearerToken("appointment-service", null,
				List.of("users:email:read"), now.minusSeconds(600), now.minusSeconds(300), testJwtKeys.keyPair())))
				.andExpect(status().isUnauthorized());

		verify(userService, never()).getUserEmail(USER_ID);
	}

	@Test
	void tokenWithIncorrectIssuerShouldBeUnauthorized() throws Exception {

		mockMvc.perform(get(emailLookupUrl()).header("Authorization", bearerToken("appointment-service", null,
				List.of("users:email:read"), null, null, testJwtKeys.keyPair(), "incorrect-issuer")))
				.andExpect(status().isUnauthorized());

		verify(userService, never()).getUserEmail(USER_ID);
	}

	private String emailLookupUrl() {

		return "/api/internal/users/" + USER_ID + "/email";
	}

	private String bearerToken(String subject, String role, List<String> scopes, Instant issuedAt, Instant expiration,
			KeyPair signingKeyPair) {

		return bearerToken(subject, role, scopes, issuedAt, expiration, signingKeyPair, ISSUER);
	}

	private String bearerToken(String subject, String role, List<String> scopes, Instant issuedAt, Instant expiration,
			KeyPair signingKeyPair, String issuer) {

		Instant now = Instant.now();

		Instant tokenIssuedAt = issuedAt != null ? issuedAt : now;

		Instant tokenExpiration = expiration != null ? expiration : now.plusSeconds(300);

		var builder = Jwts.builder().subject(subject).issuer(issuer).issuedAt(Date.from(tokenIssuedAt))
				.expiration(Date.from(tokenExpiration));

		if (role != null) {
			builder.claim("role", role);
		}

		if (scopes != null) {
			builder.claim("scope", scopes);
		}

		return "Bearer " + builder.signWith((RSAPrivateKey) signingKeyPair.getPrivate(), Jwts.SIG.RS256).compact();
	}

	private static KeyPair generateKeyPair() {

		try {

			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");

			generator.initialize(2048);

			return generator.generateKeyPair();

		} catch (Exception exception) {

			throw new IllegalStateException("Unable to generate test RSA key pair.", exception);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestJwtConfiguration {

		@Bean
		TestJwtKeys testJwtKeys() {

			return new TestJwtKeys(generateKeyPair());
		}

		@Bean
		@Primary
		JwtDecoder testJwtDecoder(TestJwtKeys testJwtKeys) {

			NimbusJwtDecoder decoder = NimbusJwtDecoder
					.withPublicKey((java.security.interfaces.RSAPublicKey) testJwtKeys.keyPair().getPublic()).build();

			decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));

			return decoder;
		}
	}

	record TestJwtKeys(KeyPair keyPair) {
	}
}
