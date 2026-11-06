
package com.medsync.notification.config;

import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	public JwtDecoder jwtDecoder(RSAPublicKey jwtPublicKey, @Value("${jwt.issuer}") String issuer) {

		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtPublicKey)
				.signatureAlgorithm(SignatureAlgorithm.RS256).build();

		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));

		return decoder;
	}

	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

		// Both human and service tokens have a subject (sub) claim.
		converter.setPrincipalClaimName("sub");

		converter.setJwtGrantedAuthoritiesConverter(jwt -> {

			Collection<GrantedAuthority> authorities = new ArrayList<>();

			// Human-user role authorities.
			String role = jwt.getClaimAsString("role");

			if (role != null && !role.isBlank()) {
				authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
			}

			// Service-token scope authorities.
			List<String> scopes = jwt.getClaimAsStringList("scope");

			if (scopes != null) {
				scopes.stream().filter(scope -> scope != null && !scope.isBlank())
						.map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).forEach(authorities::add);
			}

			return authorities;
		});

		return converter;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {

		http.csrf(csrf -> csrf.disable())

				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

				.authorizeHttpRequests(auth -> auth

						.requestMatchers("/actuator/health", "/actuator/info").permitAll()

						.requestMatchers(HttpMethod.POST, "/api/notifications")
						.hasAnyAuthority("ROLE_ADMIN", "ROLE_RECEPTIONIST", "SCOPE_notifications:create")

						.requestMatchers(HttpMethod.GET, "/api/notifications", "/api/notifications/**")
						.hasAnyRole("ADMIN", "RECEPTIONIST")

						.anyRequest().authenticated())

				.oauth2ResourceServer(
						oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

		return http.build();
	}
}
