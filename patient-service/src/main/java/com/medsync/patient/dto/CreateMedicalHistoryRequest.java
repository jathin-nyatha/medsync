package com.medsync.patient.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public class CreateMedicalHistoryRequest {

	@NotBlank(message = "Condition name is required")
	@Size(max = 150, message = "Condition name cannot exceed 150 characters")
	private String conditionName;

	@NotBlank(message = "Description is required")
	@Size(max = 2000, message = "Description cannot exceed 2000 characters")
	private String description;

	@PastOrPresent(message = "Onset date cannot be in the future")
	private LocalDate onsetDate;

	@Size(max = 2000, message = "Treatment details cannot exceed 2000 characters")
	private String treatmentDetails;

	public String getConditionName() {
		return conditionName;
	}

	public void setConditionName(String conditionName) {
		this.conditionName = conditionName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public LocalDate getOnsetDate() {
		return onsetDate;
	}

	public void setOnsetDate(LocalDate onsetDate) {
		this.onsetDate = onsetDate;
	}

	public String getTreatmentDetails() {
		return treatmentDetails;
	}

	public void setTreatmentDetails(String treatmentDetails) {
		this.treatmentDetails = treatmentDetails;
	}
}