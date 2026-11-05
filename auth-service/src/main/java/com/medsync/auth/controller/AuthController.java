package com.medsync.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medsync.auth.dto.LoginRequest;
import com.medsync.auth.dto.LoginResponse;
import com.medsync.auth.dto.PatientRegistrationRequest;
import com.medsync.auth.dto.RegisterRequest;
import com.medsync.auth.dto.RegisterResponse;
import com.medsync.auth.entity.Role;
import com.medsync.auth.entity.User;
import com.medsync.auth.security.JwtService;
import com.medsync.auth.security.MedSyncUserDetails;
import com.medsync.auth.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final UserService userService;
	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;

	public AuthController(UserService userService, AuthenticationManager authenticationManager, JwtService jwtService) {

		this.userService = userService;
		this.authenticationManager = authenticationManager;
		this.jwtService = jwtService;
	}

	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {

		User user = userService.registerUser(request.getEmail(), request.getPassword(), request.getRole());

		RegisterResponse response = new RegisterResponse(user.getId(), user.getEmail(), user.getRole());

		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {

		var authenticationRequest = UsernamePasswordAuthenticationToken.unauthenticated(request.getEmail(),
				request.getPassword());

		var authentication = authenticationManager.authenticate(authenticationRequest);

		MedSyncUserDetails userDetails = (MedSyncUserDetails) authentication.getPrincipal();

		Role role = Role.valueOf(userDetails.getRole());

		String accessToken = jwtService.generateToken(userDetails.getId(), userDetails.getUsername(), role);

		LoginResponse response = new LoginResponse(accessToken, "Bearer", userDetails.getUsername(), role,
				"Login successful");

		return ResponseEntity.ok(response);
	}

	@PostMapping("/register/patient")
	public ResponseEntity<RegisterResponse> registerPatient(@Valid @RequestBody PatientRegistrationRequest request) {

		User user = userService.registerPatient(request.getEmail(), request.getPassword());

		RegisterResponse response = new RegisterResponse(user.getId(), user.getEmail(), user.getRole());

		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}
}