package com.medsync.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProtectedController {

	@GetMapping("/api/auth/protected")
	public String protectedEndpoint() {
		return "You are authenticated.";
	}

	@GetMapping("/api/auth/admin")
	public String adminEndpoint() {
		return "Hello ADMIN, you have access to the admin endpoint.";
	}
}