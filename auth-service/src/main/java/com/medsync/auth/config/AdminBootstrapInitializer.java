package com.medsync.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.medsync.auth.entity.Role;
import com.medsync.auth.entity.User;
import com.medsync.auth.repository.UserRepository;

@Configuration
public class AdminBootstrapInitializer {

	@Bean
	public ApplicationRunner bootstrapAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder,
			@Value("${medsync.bootstrap-admin.enabled:false}") boolean enabled,
			@Value("${medsync.bootstrap-admin.email:}") String email,
			@Value("${medsync.bootstrap-admin.password:}") String password) {

		return args -> {

			if (!enabled) {
				return;
			}

			// Do not recreate an ADMIN if one already exists.
			if (userRepository.existsByRole(Role.ADMIN)) {
				System.out.println("ADMIN user already exists. Bootstrap skipped.");
				return;
			}

			if (email.isBlank() || password.isBlank()) {
				throw new IllegalStateException("Bootstrap ADMIN is enabled, but " + "BOOTSTRAP_ADMIN_EMAIL or "
						+ "BOOTSTRAP_ADMIN_PASSWORD is missing.");
			}

			if (password.length() < 12) {
				throw new IllegalStateException("Bootstrap ADMIN password must contain " + "at least 12 characters.");
			}

			if (userRepository.findByEmail(email).isPresent()) {
				throw new IllegalStateException("Bootstrap ADMIN email already belongs " + "to another user.");
			}

			String encodedPassword = passwordEncoder.encode(password);

			User admin = new User(email, encodedPassword, Role.ADMIN);

			userRepository.save(admin);

			System.out.println("Initial ADMIN user created successfully.");
		};
	}
}