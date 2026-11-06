
package com.medsync.notification.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ServiceApiKeyAuthenticationFilter extends OncePerRequestFilter {

	private static final String SERVICE_TOKEN_HEADER = "X-MedSync-Service-Token";

	private final String configuredServiceToken;

	public ServiceApiKeyAuthenticationFilter(
			@org.springframework.beans.factory.annotation.Value("${medsync.security.appointment-service-token:}") String configuredServiceToken) {

		this.configuredServiceToken = configuredServiceToken;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {

		return !HttpMethod.POST.matches(request.getMethod()) || !"/api/notifications".equals(request.getServletPath());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String suppliedToken = request.getHeader(SERVICE_TOKEN_HEADER);

		if (suppliedToken == null || suppliedToken.isBlank()) {
			filterChain.doFilter(request, response);
			return;
		}

		if (configuredServiceToken == null || configuredServiceToken.isBlank()) {
			response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "Service authentication is not configured.");
			return;
		}

		boolean validToken = MessageDigest.isEqual(configuredServiceToken.getBytes(StandardCharsets.UTF_8),
				suppliedToken.getBytes(StandardCharsets.UTF_8));

		if (!validToken) {
			SecurityContextHolder.clearContext();

			response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid service credentials.");

			return;
		}

		var authentication = UsernamePasswordAuthenticationToken.authenticated("appointment-service", null,
				List.of(new SimpleGrantedAuthority("ROLE_APPOINTMENT_SERVICE")));

		authentication.setDetails(request.getRemoteAddr());

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);

		filterChain.doFilter(request, response);
	}
}
