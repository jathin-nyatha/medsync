package com.medsync.auth.service;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medsync.auth.dto.DoctorEligibilityResponse;
import com.medsync.auth.dto.UserEmailResponse;
import com.medsync.auth.entity.Role;
import com.medsync.auth.entity.User;
import com.medsync.auth.exception.DuplicateEmailException;
import com.medsync.auth.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {

		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public User registerUser(String email, String rawPassword, Role role) {

		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

		if (userRepository.findByEmail(normalizedEmail).isPresent()) {
			throw new DuplicateEmailException("User already exists");
		}

		String encodedPassword = passwordEncoder.encode(rawPassword);

		User user = new User(normalizedEmail, encodedPassword, role);

		return userRepository.save(user);
	}

	public User registerPatient(String email, String rawPassword) {
		return registerUser(email, rawPassword, Role.PATIENT);
	}

	public Optional<User> findByEmail(String email) {

		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

		return userRepository.findByEmail(normalizedEmail);
	}

	@Transactional(readOnly = true)
	public DoctorEligibilityResponse checkDoctorEligibility(Long userId) {

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("Auth user not found with ID: " + userId));

		boolean eligible = user.getRole() == Role.DOCTOR && user.isEnabled();

		return new DoctorEligibilityResponse(user.getId(), user.getRole(), user.isEnabled(), eligible);
	}

	@Transactional(readOnly = true)
	public UserEmailResponse getUserEmail(Long userId) {

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("Auth user not found with ID: " + userId));

		return new UserEmailResponse(user.getId(), user.getEmail(), user.isEnabled());
	}
}