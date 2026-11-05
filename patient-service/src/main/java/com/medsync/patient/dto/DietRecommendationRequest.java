package com.medsync.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DietRecommendationRequest(

		@NotBlank(message = "Dietary goal is required") @Size(max = 500, message = "Dietary goal must not exceed 500 characters") String dietaryGoal,

		@Size(max = 1000, message = "Food allergies must not exceed 1000 characters") String foodAllergies,

		@Size(max = 1000, message = "Dietary preferences must not exceed 1000 characters") String dietaryPreferences,

		@Size(max = 1000, message = "Additional information must not exceed 1000 characters") String additionalInformation

) {
}