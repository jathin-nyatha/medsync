package com.medsync.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.medsync.auth.entity.Role;
import com.medsync.auth.entity.User;
import com.medsync.auth.repository.UserRepository;
import com.medsync.auth.service.UserService;

@Component
@ConditionalOnProperty(name = "medsync.bootstrap-admin.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {

	private final UserRepository userRepository;
	private final UserService userService;

	@Value("${medsync.bootstrap-admin.email:}")
	private String adminEmail;

	@Value("${medsync.bootstrap-admin.password:}")
	private String adminPassword;

	public AdminBootstrapRunner(UserRepository userRepository, UserService userService) {

		this.userRepository = userRepository;
		this.userService = userService;
	}

	@Override
	public void run(ApplicationArguments args) {

		boolean adminExists = userRepository.findAll().stream().anyMatch(user -> user.getRole() == Role.ADMIN);

		if (adminExists) {
			System.out.println("Bootstrap skipped: an ADMIN account already exists.");
			return;
		}

		if (adminEmail == null || adminEmail.isBlank()) {
			throw new IllegalStateException("Bootstrap admin email is not configured.");
		}

		if (adminPassword == null || adminPassword.length() < 12) {
			throw new IllegalStateException("Bootstrap admin password must contain at least 12 characters.");
		}

		User admin = userService.registerUser(adminEmail, adminPassword, Role.ADMIN);

		if (admin.getRole() != Role.ADMIN || !admin.isEnabled()) {
			throw new IllegalStateException("Bootstrap administrator was not created correctly.");
		}

		System.out.println("Bootstrap administrator created successfully: " + admin.getEmail());
	}
}