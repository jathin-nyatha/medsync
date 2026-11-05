
package com.medsync.patient.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medsync.patient.config.SecurityConfig;
import com.medsync.patient.dto.DietRecommendationRequest;
import com.medsync.patient.dto.DietRecommendationResponse;
import com.medsync.patient.service.DietRecommendationService;

@WebMvcTest(DietRecommendationController.class)
@Import(SecurityConfig.class)
class DietRecommendationControllerTest {

	private static final String BASE_URL = "/api/patients/101/diet-recommendations";

	private static final String TEST_TOKEN = "test-token";

	private static final String AUTHORIZATION_HEADER = "Bearer " + TEST_TOKEN;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DietRecommendationService dietRecommendationService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	/*
	 * Creates a JWT accepted by the mocked JwtDecoder.
	 */
	private void mockJwt(Long userId, String role) throws JwtException {

		Jwt jwt = Jwt.withTokenValue(TEST_TOKEN).header("alg", "RS256").claim("userId", userId).claim("role", role)
				.build();

		when(jwtDecoder.decode(TEST_TOKEN)).thenReturn(jwt);
	}

	/*
	 * Request JSON matching the actual DietRecommendationRequest record.
	 *
	 * All four fields are Strings, not arrays.
	 */
	private String validRequestJson() {

		return """
				{
				    "dietaryGoal": "Weight management",
				    "foodAllergies": "Peanuts",
				    "dietaryPreferences": "Vegetarian",
				    "additionalInformation": "Prefers simple home-cooked meals"
				}
				""";
	}

	/*
	 * Sample response returned by the mocked service.
	 */
	private DietRecommendationResponse sampleResponse() {

		return new DietRecommendationResponse("A balanced vegetarian diet with appropriate portion sizes.", List.of(),
				List.of("Avoid peanuts due to the reported allergy."), true,
				"This recommendation is for educational purposes only and requires clinician review.");
	}

	// ---------------------------------------------------------
	// TEST 1: Authenticated doctor can generate recommendation
	// ---------------------------------------------------------

	@Test
	void generateDietRecommendation_WhenDoctorAuthenticated_Returns200() throws Exception {

		mockJwt(25L, "DOCTOR");

		DietRecommendationResponse response = sampleResponse();

		when(dietRecommendationService.generateDietRecommendation(eq(101L), eq(25L), eq(AUTHORIZATION_HEADER),
				any(DietRecommendationRequest.class))).thenReturn(response);

		mockMvc.perform(post(BASE_URL).header("Authorization", AUTHORIZATION_HEADER)
				.contentType(MediaType.APPLICATION_JSON).content(validRequestJson())).andExpect(status().isOk())
				.andExpect(jsonPath("$.recommendation").exists())
				.andExpect(jsonPath("$.clinicianReviewRequired").value(true))
				.andExpect(jsonPath("$.disclaimer").exists());

		verify(dietRecommendationService).generateDietRecommendation(eq(101L), eq(25L), eq(AUTHORIZATION_HEADER),
				any(DietRecommendationRequest.class));
	}

	// ---------------------------------------------------------
	// TEST 2: Unauthenticated user receives 401
	// ---------------------------------------------------------

	@Test
	void generateDietRecommendation_WhenUnauthenticated_Returns401() throws Exception {

		mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(validRequestJson()))
				.andExpect(status().isUnauthorized());
	}

	// ---------------------------------------------------------
	// TEST 3: Receptionist receives 403
	// ---------------------------------------------------------

	@Test
	void generateDietRecommendation_WhenReceptionistAuthenticated_Returns403() throws Exception {

		mockJwt(30L, "RECEPTIONIST");

		mockMvc.perform(post(BASE_URL).header("Authorization", AUTHORIZATION_HEADER)
				.contentType(MediaType.APPLICATION_JSON).content(validRequestJson())).andExpect(status().isForbidden());
	}

	// ---------------------------------------------------------
	// TEST 4: Invalid request receives 400
	// ---------------------------------------------------------

	@Test
	void generateDietRecommendation_WhenRequestInvalid_Returns400() throws Exception {

		mockJwt(25L, "DOCTOR");

		String invalidRequestJson = """
				{
				    "dietaryGoal": "",
				    "foodAllergies": "Peanuts",
				    "dietaryPreferences": "Vegetarian",
				    "additionalInformation": "Prefers home-cooked meals"
				}
				""";

		mockMvc.perform(post(BASE_URL).header("Authorization", AUTHORIZATION_HEADER)
				.contentType(MediaType.APPLICATION_JSON).content(invalidRequestJson))
				.andExpect(status().isBadRequest());
	}
}
