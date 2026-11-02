package com.medsync.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medsync.auth.dto.RegisterRequest;
import com.medsync.auth.dto.RegisterResponse;
import com.medsync.auth.entity.User;
import com.medsync.auth.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final UserService userService;

	public AuthController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {

		User user = userService.registerUser(request.getEmail(), request.getPassword(), request.getRole());

		RegisterResponse response = new RegisterResponse(user.getId(), user.getEmail(), user.getRole());

		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}
}