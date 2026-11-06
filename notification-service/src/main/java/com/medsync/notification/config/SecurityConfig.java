
package com.medsync.notification.config;

import java.security.interfaces.RSAPublicKey;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
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

		converter.setPrincipalClaimName("userId");

		converter.setJwtGrantedAuthoritiesConverter(jwt -> {

			String role = jwt.getClaimAsString("role");

			if (role == null || role.isBlank()) {
				return List.of();
			}

			return List.of(new SimpleGrantedAuthority("ROLE_" + role));
		});

		return converter;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			JwtAuthenticationConverter jwtAuthenticationConverter,
			ServiceApiKeyAuthenticationFilter serviceApiKeyAuthenticationFilter) throws Exception {

		http.csrf(csrf -> csrf.disable())

				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

				.authorizeHttpRequests(auth -> auth

						.requestMatchers("/actuator/health", "/actuator/info").permitAll()

						.requestMatchers(HttpMethod.POST, "/api/notifications")
						.hasAnyRole("ADMIN", "RECEPTIONIST", "APPOINTMENT_SERVICE")

						.requestMatchers(HttpMethod.GET, "/api/notifications", "/api/notifications/**")
						.hasAnyRole("ADMIN", "RECEPTIONIST")

						.anyRequest().authenticated())

				.oauth2ResourceServer(
						oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))

				.addFilterAfter(serviceApiKeyAuthenticationFilter, BearerTokenAuthenticationFilter.class);

		return http.build();
	}
}
