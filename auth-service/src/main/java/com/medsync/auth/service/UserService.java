package com.medsync.auth.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.medsync.auth.dto.DoctorEligibilityResponse;
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

		if (userRepository.findByEmail(email).isPresent()) {
			throw new DuplicateEmailException("User already exists");
		}

		String encodedPassword = passwordEncoder.encode(rawPassword);

		User user = new User(email, encodedPassword, role);

		return userRepository.save(user);
	}

	public Optional<User> findByEmail(String email) {
		return userRepository.findByEmail(email);
	}

	public DoctorEligibilityResponse checkDoctorEligibility(Long userId) {

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("Auth user not found with ID: " + userId));

		boolean eligible = user.getRole() == Role.DOCTOR && user.isEnabled();

		return new DoctorEligibilityResponse(user.getId(), user.getRole(), user.isEnabled(), eligible);
	}

}