package com.medsync.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
public class JwtAuthenticationConfig {

	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {

		JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();

		// Read our custom "role" claim instead of the default "scope"/"scp"
		authoritiesConverter.setAuthoritiesClaimName("role");

		// Convert ADMIN -> ROLE_ADMIN
		authoritiesConverter.setAuthorityPrefix("ROLE_");

		JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();

		authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

		return authenticationConverter;
	}
}