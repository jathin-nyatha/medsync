package com.medsync.auth.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtKeyConfig {

	@Bean
	public PrivateKey jwtPrivateKey(@Value("${jwt.keystore.path}") String keystorePath,
			@Value("${jwt.keystore.password}") String keystorePassword, @Value("${jwt.keystore.alias}") String keyAlias)
			throws Exception {

		KeyStore keyStore = KeyStore.getInstance("PKCS12");

		try (InputStream inputStream = Files.newInputStream(Path.of(keystorePath))) {
			keyStore.load(inputStream, keystorePassword.toCharArray());
		}

		return (PrivateKey) keyStore.getKey(keyAlias, keystorePassword.toCharArray());
	}

	@Bean
	public RSAPublicKey jwtPublicKey(@Value("${jwt.keystore.path}") String keystorePath,
			@Value("${jwt.keystore.password}") String keystorePassword, @Value("${jwt.keystore.alias}") String keyAlias)
			throws Exception {

		KeyStore keyStore = KeyStore.getInstance("PKCS12");

		try (InputStream inputStream = Files.newInputStream(Path.of(keystorePath))) {
			keyStore.load(inputStream, keystorePassword.toCharArray());
		}

		Certificate certificate = keyStore.getCertificate(keyAlias);

		return (RSAPublicKey) certificate.getPublicKey();
	}
}