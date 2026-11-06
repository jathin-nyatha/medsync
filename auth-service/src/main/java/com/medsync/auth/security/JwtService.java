package com.medsync.auth.security;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.medsync.auth.entity.Role;

import io.jsonwebtoken.Jwts;

@Service
public class JwtService {

	private final PrivateKey signingKey;
	private final String issuer;
	private final long expiration;
	private final long serviceTokenExpirationSeconds;

	public JwtService(PrivateKey jwtPrivateKey, @Value("${jwt.issuer}") String issuer,
			@Value("${jwt.expiration}") long expiration,
			@Value("${jwt.service-token-expiration-seconds:300}") long serviceTokenExpirationSeconds) {

		if (serviceTokenExpirationSeconds <= 0) {
			throw new IllegalArgumentException("Service token expiration must be positive.");
		}

		this.signingKey = jwtPrivateKey;
		this.issuer = issuer;
		this.expiration = expiration;
		this.serviceTokenExpirationSeconds = serviceTokenExpirationSeconds;
	}

	// Human-user JWT.
	public String generateToken(Long userId, String email, Role role) {

		Instant now = Instant.now();

		return Jwts.builder().subject(email).claim("userId", userId).claim("role", role.name()).issuer(issuer)
				.issuedAt(Date.from(now)).expiration(Date.from(now.plusMillis(expiration)))
				.signWith(signingKey, Jwts.SIG.RS256).compact();
	}

	// Service-to-service JWT.
	public String generateServiceToken(String clientId, List<String> scopes) {

		Instant now = Instant.now();

		return Jwts.builder().subject(clientId).claim("token_type", "service").claim("scope", scopes).issuer(issuer)
				.issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(serviceTokenExpirationSeconds)))
				.signWith(signingKey, Jwts.SIG.RS256).compact();
	}

	public long getServiceTokenExpirationSeconds() {
		return serviceTokenExpirationSeconds;
	}
}