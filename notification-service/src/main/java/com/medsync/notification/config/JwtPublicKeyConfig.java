package com.medsync.notification.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtPublicKeyConfig {

	@Bean
	public java.security.interfaces.RSAPublicKey jwtPublicKey(@Value("${jwt.public-key-path}") String publicKeyPath) {

		try (InputStream inputStream = Files.newInputStream(Path.of(publicKeyPath))) {

			CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

			X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(inputStream);

			return (java.security.interfaces.RSAPublicKey) certificate.getPublicKey();

		} catch (Exception exception) {
			throw new IllegalStateException("Unable to load JWT public key from: " + publicKeyPath, exception);
		}
	}
}