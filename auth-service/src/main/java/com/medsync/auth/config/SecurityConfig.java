
package com.medsync.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {

		http.csrf(csrf -> csrf.disable())

				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

				.authorizeHttpRequests(authorize -> authorize

						// Public endpoints.
						.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()

						.requestMatchers(HttpMethod.POST, "/api/auth/register/patient").permitAll()

						.requestMatchers(HttpMethod.POST, "/api/auth/service-token").permitAll()

						.requestMatchers("/actuator/health").permitAll()

						// Email lookup:
						// ADMIN, RECEPTIONIST, or authorized service.
						.requestMatchers(HttpMethod.GET, "/api/internal/users/*/email")
						.access((authentication, context) -> {

							var authorities = authentication.get().getAuthorities();

							boolean allowed = authorities.stream().anyMatch(authority -> {

								String name = authority.getAuthority();

								return name.equals("ROLE_ADMIN") || name.equals("ROLE_RECEPTIONIST")
										|| name.equals("SCOPE_users:email:read");
							});

							return new AuthorizationDecision(allowed);
						})

						// Administrator-only endpoints.
						.requestMatchers("/api/auth/register").hasRole("ADMIN")

						.requestMatchers("/api/auth/admin").hasRole("ADMIN")

						// Other internal endpoints remain ADMIN-only.
						.requestMatchers("/api/internal/**").hasRole("ADMIN")

						// Authenticated endpoints.
						.requestMatchers("/api/auth/protected").authenticated()

						.anyRequest().authenticated())

				.oauth2ResourceServer(
						oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

		return http.build();
	}

	@Bean
	public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {

		DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userDetailsService);

		authenticationProvider.setPasswordEncoder(passwordEncoder);

		return new ProviderManager(authenticationProvider);
	}
}
