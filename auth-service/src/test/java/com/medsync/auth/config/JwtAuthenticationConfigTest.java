
package com.medsync.auth.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

class JwtAuthenticationConfigTest {

	private JwtAuthenticationConverter authenticationConverter;

	@BeforeEach
	void setUp() {

		JwtAuthenticationConfig config = new JwtAuthenticationConfig();

		authenticationConverter = config.jwtAuthenticationConverter();
	}

	@Test
	void shouldConvertHumanUserRoleIntoRoleAuthority() {

		Jwt jwt = createJwt("ADMIN", null);

		Set<String> authorities = getAuthorityNames(jwt);

		assertTrue(authorities.contains("ROLE_ADMIN"));

		assertTrue(!authorities.contains("SCOPE_ADMIN"));
	}

	@Test
	void shouldConvertServiceScopesIntoScopeAuthorities() {

		Jwt jwt = createJwt(null, List.of("notifications:create", "users:email:read"));

		Set<String> authorities = getAuthorityNames(jwt);

		assertTrue(authorities.contains("SCOPE_notifications:create"));

		assertTrue(authorities.contains("SCOPE_users:email:read"));

		assertTrue(!authorities.contains("ROLE_notifications:create"));
	}

	@Test
	void shouldCombineRoleAndScopeAuthorities() {

		Jwt jwt = createJwt("RECEPTIONIST", List.of("notifications:create"));

		Set<String> authorities = getAuthorityNames(jwt);

		assertTrue(authorities.contains("ROLE_RECEPTIONIST"));

		assertTrue(authorities.contains("SCOPE_notifications:create"));
	}

	private Set<String> getAuthorityNames(Jwt jwt) {

		Collection<GrantedAuthority> authorities = authenticationConverter.convert(jwt).getAuthorities();

		return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
	}

	private Jwt createJwt(String role, List<String> scopes) {

		Instant now = Instant.now();

		Jwt.Builder builder = Jwt.withTokenValue("test-token").header("alg", "RS256").issuedAt(now)
				.expiresAt(now.plusSeconds(300));

		if (role != null) {
			builder.claim("role", role);
		}

		if (scopes != null) {
			builder.claim("scope", scopes);
		}

		return builder.build();
	}
}
