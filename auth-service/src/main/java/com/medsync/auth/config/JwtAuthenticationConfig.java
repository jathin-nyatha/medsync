
package com.medsync.auth.config;

import java.util.Collection;
import java.util.stream.Stream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
public class JwtAuthenticationConfig {

	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {

		// Convert custom role claim into ROLE_* authorities.
		JwtGrantedAuthoritiesConverter roleAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

		roleAuthoritiesConverter.setAuthoritiesClaimName("role");
		roleAuthoritiesConverter.setAuthorityPrefix("ROLE_");

		// Convert scope claim into SCOPE_* authorities.
		JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

		scopeAuthoritiesConverter.setAuthoritiesClaimName("scope");
		scopeAuthoritiesConverter.setAuthorityPrefix("SCOPE_");

		// Combine both authority collections.
		Converter<Jwt, Collection<GrantedAuthority>> combinedAuthoritiesConverter = jwt -> Stream
				.concat(roleAuthoritiesConverter.convert(jwt).stream(), scopeAuthoritiesConverter.convert(jwt).stream())
				.toList();

		JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();

		authenticationConverter.setJwtGrantedAuthoritiesConverter(combinedAuthoritiesConverter);

		return authenticationConverter;
	}
}
