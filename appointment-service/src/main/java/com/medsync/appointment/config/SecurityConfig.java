package com.medsync.appointment.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Value("${jwt.public-key-path}")
	private String publicKeyPath;

	@Value("${jwt.issuer}")
	private String issuer;

	@Bean
	public RSAPublicKey jwtPublicKey() throws Exception {

		CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

		try (InputStream inputStream = Files.newInputStream(Path.of(publicKeyPath))) {

			X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(inputStream);

			return (RSAPublicKey) certificate.getPublicKey();
		}
	}

	@Bean
	public JwtDecoder jwtDecoder(RSAPublicKey jwtPublicKey) {

		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtPublicKey).build();

		OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(issuer));

		decoder.setJwtValidator(validator);

		return decoder;
	}

	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

		converter.setPrincipalClaimName("userId");

		Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter = jwt -> {

			String role = jwt.getClaimAsString("role");

			if (role == null || role.isBlank()) {
				return List.of();
			}

			return List.of(new SimpleGrantedAuthority("ROLE_" + role));
		};

		converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

		return converter;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {

		http.csrf(AbstractHttpConfigurer::disable)

				.authorizeHttpRequests(authorize -> authorize.requestMatchers("/actuator/health", "/actuator/info")
						.permitAll().requestMatchers("/api/internal/assignments/**").hasRole("DOCTOR").anyRequest()
						.authenticated())

				.oauth2ResourceServer(
						oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

		return http.build();
	}
}