package com.medsync.patient.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.medsync.patient.dto.DietRecommendationRequest;
import com.medsync.patient.dto.DietRecommendationResponse;
import com.medsync.patient.entity.Patient;
import com.medsync.patient.exception.PatientNotFoundException;
import com.medsync.patient.repository.DiagnosisRepository;
import com.medsync.patient.repository.MedicalHistoryRepository;
import com.medsync.patient.repository.PatientRepository;

@ExtendWith(MockitoExtension.class)
class DietRecommendationServiceTest {

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private ChatClient chatClient;

	@Mock
	private PatientRepository patientRepository;

	@Mock
	private MedicalHistoryRepository medicalHistoryRepository;

	@Mock
	private DiagnosisRepository diagnosisRepository;

	@Mock
	private PatientAccessService patientAccessService;

	private DietRecommendationService dietRecommendationService;

	@BeforeEach
	void setUp() {
		dietRecommendationService = new DietRecommendationService(chatClient, patientRepository,
				medicalHistoryRepository, diagnosisRepository, patientAccessService);
	}

	@Test
	void generateDietRecommendation_shouldReturnStructuredResponse() {

		// Arrange
		Long patientId = 101L;
		Long doctorAuthUserId = 25L;
		String authorizationHeader = "Bearer test-token";

		DietRecommendationRequest request = new DietRecommendationRequest("Balanced nutrition", "Peanuts", "Vegetarian",
				"Avoid very spicy food");

		Patient patient = mock(Patient.class);

		when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

		when(medicalHistoryRepository.findByPatient_Id(org.mockito.ArgumentMatchers.eq(patientId),
				org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(Page.empty());

		when(diagnosisRepository.findByPatient_Id(org.mockito.ArgumentMatchers.eq(patientId),
				org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(Page.empty());

		DietRecommendationResponse aiResponse = new DietRecommendationResponse(
				"Choose a varied and balanced vegetarian diet.",
				List.of(new DietRecommendationResponse.MealSuggestion("Breakfast", List.of("Oatmeal", "Fresh fruit"),
						"Choose foods that suit your preferences.")),
				List.of("Check food labels for peanut ingredients."), false, "AI-generated information.");

		when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(DietRecommendationResponse.class))
				.thenReturn(aiResponse);

		// Act
		DietRecommendationResponse response = dietRecommendationService.generateDietRecommendation(patientId,
				doctorAuthUserId, authorizationHeader, request);

		// Assert
		assertNotNull(response);
		assertEquals("Choose a varied and balanced vegetarian diet.", response.recommendation());

		assertEquals(1, response.mealSuggestions().size());
		assertEquals("Breakfast", response.mealSuggestions().get(0).mealType());

		assertFalse(response.safetyNotes().isEmpty());

		// The application must override the AI's false value.
		assertTrue(response.clinicianReviewRequired());

		assertNotNull(response.disclaimer());
		assertFalse(response.disclaimer().isBlank());

		verify(patientAccessService).validateDoctorAccess(patientId, doctorAuthUserId, authorizationHeader);
	}

	@Test
	void generateDietRecommendation_shouldThrowExceptionWhenPatientDoesNotExist() {

		Long patientId = 999L;
		Long doctorAuthUserId = 25L;
		String authorizationHeader = "Bearer test-token";

		DietRecommendationRequest request = new DietRecommendationRequest("Balanced nutrition", null, "Vegetarian",
				null);

		when(patientRepository.findById(patientId)).thenReturn(Optional.empty());

		assertThrows(PatientNotFoundException.class, () -> dietRecommendationService
				.generateDietRecommendation(patientId, doctorAuthUserId, authorizationHeader, request));

		verify(patientAccessService).validateDoctorAccess(patientId, doctorAuthUserId, authorizationHeader);

		verifyNoInteractions(medicalHistoryRepository);
		verifyNoInteractions(diagnosisRepository);
	}

	@Test
	void generateDietRecommendation_shouldStopWhenDoctorAccessIsDenied() {

		Long patientId = 101L;
		Long doctorAuthUserId = 25L;
		String authorizationHeader = "Bearer test-token";

		DietRecommendationRequest request = new DietRecommendationRequest("Balanced nutrition", null, null, null);

		doThrow(new RuntimeException("Doctor access denied")).when(patientAccessService).validateDoctorAccess(patientId,
				doctorAuthUserId, authorizationHeader);

		assertThrows(RuntimeException.class, () -> dietRecommendationService.generateDietRecommendation(patientId,
				doctorAuthUserId, authorizationHeader, request));

		verifyNoInteractions(patientRepository);
		verifyNoInteractions(medicalHistoryRepository);
		verifyNoInteractions(diagnosisRepository);
		verifyNoInteractions(chatClient);
	}

	@Test
	void generateDietRecommendation_shouldHandleOllamaFailure() {

		Long patientId = 101L;
		Long doctorAuthUserId = 25L;
		String authorizationHeader = "Bearer test-token";

		DietRecommendationRequest request = new DietRecommendationRequest("Balanced nutrition", null, "Vegetarian",
				null);

		when(patientRepository.findById(patientId)).thenReturn(Optional.of(mock(Patient.class)));

		when(medicalHistoryRepository.findByPatient_Id(org.mockito.ArgumentMatchers.eq(patientId),
				org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(Page.empty());

		when(diagnosisRepository.findByPatient_Id(org.mockito.ArgumentMatchers.eq(patientId),
				org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(Page.empty());

		when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(DietRecommendationResponse.class))
				.thenThrow(new RuntimeException("Ollama is unavailable"));

		assertThrows(IllegalStateException.class, () -> dietRecommendationService.generateDietRecommendation(patientId,
				doctorAuthUserId, authorizationHeader, request));
	}

	@Test
	void generateDietRecommendation_shouldRejectEmptyAiResponse() {

		Long patientId = 101L;
		Long doctorAuthUserId = 25L;
		String authorizationHeader = "Bearer test-token";

		DietRecommendationRequest request = new DietRecommendationRequest("Balanced nutrition", null, null, null);

		when(patientRepository.findById(patientId)).thenReturn(Optional.of(mock(Patient.class)));

		when(medicalHistoryRepository.findByPatient_Id(org.mockito.ArgumentMatchers.eq(patientId),
				org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(Page.empty());

		when(diagnosisRepository.findByPatient_Id(org.mockito.ArgumentMatchers.eq(patientId),
				org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(Page.empty());

		when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(DietRecommendationResponse.class))
				.thenReturn(null);

		assertThrows(IllegalStateException.class, () -> dietRecommendationService.generateDietRecommendation(patientId,
				doctorAuthUserId, authorizationHeader, request));
	}
}