package com.medsync.doctor.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtPublicKeyConfig {

	@Bean
	public RSAPublicKey jwtPublicKey(@Value("${jwt.public-key-path}") String certificatePath) throws Exception {

		CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

		try (InputStream inputStream = Files.newInputStream(Path.of(certificatePath))) {

			X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(inputStream);

			return (RSAPublicKey) certificate.getPublicKey();
		}
	}

	@Bean
	public JwtDecoder jwtDecoder(RSAPublicKey jwtPublicKey, @Value("${jwt.issuer}") String issuer) {

		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtPublicKey).build();

		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));

		return decoder;
	}
}