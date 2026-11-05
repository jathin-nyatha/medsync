
package com.medsync.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateDiagnosisRequest {

	@NotBlank(message = "Diagnosis name is required")
	@Size(max = 150, message = "Diagnosis name cannot exceed 150 characters")
	private String diagnosisName;

	@Size(max = 2000, message = "Description cannot exceed 2000 characters")
	private String description;

	public String getDiagnosisName() {
		return diagnosisName;
	}

	public void setDiagnosisName(String diagnosisName) {
		this.diagnosisName = diagnosisName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
}
