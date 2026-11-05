package com.medsync.patient.dto;

import java.util.List;

public record DietRecommendationResponse(

		String recommendation,

		List<MealSuggestion> mealSuggestions,

		List<String> safetyNotes,

		boolean clinicianReviewRequired,

		String disclaimer

) {

	public record MealSuggestion(

			String mealType,

			List<String> suggestedFoods,

			String notes

	) {
	}
}