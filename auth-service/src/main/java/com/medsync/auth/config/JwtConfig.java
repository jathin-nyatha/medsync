
package com.medsync.auth.config;

import java.security.interfaces.RSAPublicKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtConfig {

	@Bean
	public JwtDecoder jwtDecoder(RSAPublicKey jwtPublicKey, @Value("${jwt.issuer}") String issuer) {

		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtPublicKey).build();

		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));

		return decoder;
	}
}
