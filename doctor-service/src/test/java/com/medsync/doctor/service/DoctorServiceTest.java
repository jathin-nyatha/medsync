
package com.medsync.doctor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.medsync.doctor.client.AuthServiceClient;
import com.medsync.doctor.dto.DoctorEligibilityResponse;
import com.medsync.doctor.entity.Doctor;
import com.medsync.doctor.entity.DoctorStatus;
import com.medsync.doctor.exception.AuthServiceAuthenticationException;
import com.medsync.doctor.exception.AuthServiceAuthorizationException;
import com.medsync.doctor.exception.AuthServiceUnavailableException;
import com.medsync.doctor.exception.DoctorNotFoundException;
import com.medsync.doctor.exception.DuplicateDoctorException;
import com.medsync.doctor.exception.InvalidAuthUserException;
import com.medsync.doctor.exception.InvalidPaginationException;
import com.medsync.doctor.repository.DoctorRepository;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

	@Mock
	private DoctorRepository doctorRepository;

	@Mock
	private AuthServiceClient authServiceClient;

	@InjectMocks
	private DoctorService doctorService;

	private Doctor doctor;

	@BeforeEach
	void setUp() {
		doctor = new Doctor(2L, "Arjun", "Sharma", "Cardiology", "TS-MED-2026-00125", "+919876543210");
	}

	@Test
	void getDoctorById_WhenDoctorExists_ReturnsDoctor() {
		when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));

		Doctor result = doctorService.getDoctorById(1L);

		assertNotNull(result);
		assertEquals("Arjun", result.getFirstName());
		verify(doctorRepository).findById(1L);
	}

	@Test
	void getDoctorById_WhenDoctorDoesNotExist_ThrowsException() {
		when(doctorRepository.findById(99L)).thenReturn(Optional.empty());

		assertThrows(DoctorNotFoundException.class, () -> doctorService.getDoctorById(99L));
	}

	@Test
	void searchDoctors_WhenStatusIsNull_ReturnsAllDoctors() {
		Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());

		Page<Doctor> expectedPage = new PageImpl<>(java.util.List.of(doctor), pageable, 1);

		when(doctorRepository.findAll(pageable)).thenReturn(expectedPage);

		Page<Doctor> result = doctorService.searchDoctors(null, pageable);

		assertEquals(1, result.getTotalElements());
		assertEquals("Arjun", result.getContent().get(0).getFirstName());

		verify(doctorRepository).findAll(pageable);
		verify(doctorRepository, never()).findByStatus(any(), any());
	}

	@Test
	void searchDoctors_WhenStatusProvided_FiltersDoctors() {
		Pageable pageable = PageRequest.of(0, 10);

		Page<Doctor> expectedPage = new PageImpl<>(java.util.List.of(doctor), pageable, 1);

		when(doctorRepository.findByStatus(DoctorStatus.ACTIVE, pageable)).thenReturn(expectedPage);

		Page<Doctor> result = doctorService.searchDoctors(DoctorStatus.ACTIVE, pageable);

		assertEquals(1, result.getTotalElements());

		verify(doctorRepository).findByStatus(DoctorStatus.ACTIVE, pageable);
	}

	@Test
	void searchDoctors_WhenUnsupportedSortField_ThrowsException() {
		Pageable pageable = PageRequest.of(0, 10, Sort.by("password"));

		assertThrows(InvalidPaginationException.class, () -> doctorService.searchDoctors(null, pageable));

		verifyNoInteractions(doctorRepository);
	}

	@Test
	void createDoctor_WhenAuthUserIsEligible_CreatesDoctor() {
		DoctorEligibilityResponse eligibility = new DoctorEligibilityResponse();
		eligibility.setEligible(true);

		when(authServiceClient.checkDoctorEligibility(eq(2L), anyString())).thenReturn(eligibility);

		when(doctorRepository.existsByAuthUserId(2L)).thenReturn(false);
		when(doctorRepository.existsByLicenseNumber("TS-MED-2026-00125")).thenReturn(false);

		when(doctorRepository.save(any(Doctor.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Doctor result = doctorService.createDoctor(2L, "Arjun", "Sharma", "Cardiology", "TS-MED-2026-00125",
				"+919876543210", "Bearer test-token");

		assertNotNull(result);
		assertEquals("Arjun", result.getFirstName());
		assertEquals(2L, result.getAuthUserId());

		verify(authServiceClient).checkDoctorEligibility(2L, "Bearer test-token");

		verify(doctorRepository).save(any(Doctor.class));
	}

	@Test
	void createDoctor_WhenProfileAlreadyExists_ThrowsDuplicateException() {
		DoctorEligibilityResponse eligibility = new DoctorEligibilityResponse();
		eligibility.setEligible(true);

		when(authServiceClient.checkDoctorEligibility(eq(2L), anyString())).thenReturn(eligibility);

		when(doctorRepository.existsByAuthUserId(2L)).thenReturn(true);

		assertThrows(DuplicateDoctorException.class, () -> doctorService.createDoctor(2L, "Arjun", "Sharma",
				"Cardiology", "TS-MED-2026-00125", "+919876543210", "Bearer test-token"));

		verify(doctorRepository, never()).save(any(Doctor.class));
	}

	@Test
	void createDoctor_WhenAuthServiceReturns401_ThrowsAuthenticationException() {
		when(authServiceClient.checkDoctorEligibility(eq(2L), anyString())).thenThrow(HttpClientErrorException
				.create(HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, null, null));

		assertThrows(AuthServiceAuthenticationException.class, () -> doctorService.createDoctor(2L, "Arjun", "Sharma",
				"Cardiology", "TS-MED-2026-00125", "+919876543210", "Bearer invalid-token"));

		verify(doctorRepository, never()).save(any(Doctor.class));
	}

	@Test
	void createDoctor_WhenAuthServiceReturns403_ThrowsAuthorizationException() {
		when(authServiceClient.checkDoctorEligibility(eq(2L), anyString())).thenThrow(
				HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, null, null));

		assertThrows(AuthServiceAuthorizationException.class, () -> doctorService.createDoctor(2L, "Arjun", "Sharma",
				"Cardiology", "TS-MED-2026-00125", "+919876543210", "Bearer receptionist-token"));
	}

	@Test
	void createDoctor_WhenAuthServiceReturns404_ThrowsInvalidAuthUserException() {
		when(authServiceClient.checkDoctorEligibility(eq(99L), anyString())).thenThrow(
				HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null));

		assertThrows(InvalidAuthUserException.class, () -> doctorService.createDoctor(99L, "Arjun", "Sharma",
				"Cardiology", "TS-MED-2026-00125", "+919876543210", "Bearer admin-token"));
	}

	@Test
	void createDoctor_WhenAuthServiceIsUnavailable_ThrowsServiceUnavailableException() {
		when(authServiceClient.checkDoctorEligibility(eq(2L), anyString()))
				.thenThrow(new ResourceAccessException("Connection timed out"));

		assertThrows(AuthServiceUnavailableException.class, () -> doctorService.createDoctor(2L, "Arjun", "Sharma",
				"Cardiology", "TS-MED-2026-00125", "+919876543210", "Bearer admin-token"));
	}
}
