
package com.medsync.auth.security;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.medsync.auth.entity.Role;

import io.jsonwebtoken.Jwts;

@Service
public class JwtService {

	private final PrivateKey signingKey;
	private final String issuer;
	private final long expiration;

	public JwtService(PrivateKey jwtPrivateKey, @Value("${jwt.issuer}") String issuer,
			@Value("${jwt.expiration}") long expiration) {

		this.signingKey = jwtPrivateKey;
		this.issuer = issuer;
		this.expiration = expiration;
	}

	public String generateToken(Long userId, String email, Role role) {

		Instant now = Instant.now();

		return Jwts.builder().subject(email).claim("userId", userId).claim("role", role.name()).issuer(issuer)
				.issuedAt(Date.from(now)).expiration(Date.from(now.plusMillis(expiration)))
				.signWith(signingKey, Jwts.SIG.RS256).compact();
	}
}
