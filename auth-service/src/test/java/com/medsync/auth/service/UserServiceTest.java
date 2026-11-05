package com.medsync.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.medsync.auth.entity.Role;
import com.medsync.auth.entity.User;
import com.medsync.auth.exception.DuplicateEmailException;
import com.medsync.auth.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private UserService userService;

	private static final String EMAIL = "patient@example.com";
	private static final String RAW_PASSWORD = "SecurePass@123";
	private static final String ENCODED_PASSWORD = "encoded-password";

	@Test
	void registerPatient_shouldCreatePatientAccount() {

		when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		User result = userService.registerPatient(EMAIL, RAW_PASSWORD);

		assertEquals(EMAIL, result.getEmail());
		assertEquals(Role.PATIENT, result.getRole());
		assertEquals(ENCODED_PASSWORD, result.getPassword());
		assertTrue(result.isEnabled());

		verify(passwordEncoder).encode(RAW_PASSWORD);
		verify(userRepository).save(any(User.class));
	}

	@Test
	void registerUser_shouldNormalizeEmail() {

		when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		User result = userService.registerUser(" Patient@Example.com ", RAW_PASSWORD, Role.PATIENT);

		assertEquals(EMAIL, result.getEmail());

		verify(userRepository).findByEmail(EMAIL);
	}

	@Test
	void registerUser_shouldRejectDuplicateEmail() {

		User existingUser = new User(EMAIL, ENCODED_PASSWORD, Role.PATIENT);

		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser));

		assertThrows(DuplicateEmailException.class, () -> userService.registerPatient(EMAIL, RAW_PASSWORD));

		verify(passwordEncoder, never()).encode(any());
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void registerUser_shouldPreserveAdministratorAssignedRole() {

		when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);

		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		User result = userService.registerUser(EMAIL, RAW_PASSWORD, Role.DOCTOR);

		assertEquals(Role.DOCTOR, result.getRole());
	}
}