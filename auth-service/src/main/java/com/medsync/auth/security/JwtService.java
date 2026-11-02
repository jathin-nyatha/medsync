package com.medsync.auth.security;

import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.medsync.auth.entity.Role;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	private final SecretKey signingKey;
	private final String issuer;
	private final long expiration;

	public JwtService(@Value("${jwt.secret}") String secret, @Value("${jwt.issuer}") String issuer,
			@Value("${jwt.expiration}") long expiration) {

		byte[] keyBytes = Decoders.BASE64.decode(secret);

		this.signingKey = Keys.hmacShaKeyFor(keyBytes);
		this.issuer = issuer;
		this.expiration = expiration;
	}

	public String generateToken(String email, Role role) {

		Instant now = Instant.now();

		return Jwts.builder().subject(email).claim("role", role.name()).issuer(issuer).issuedAt(Date.from(now))
				.expiration(Date.from(now.plusMillis(expiration))).signWith(signingKey).compact();
	}
}