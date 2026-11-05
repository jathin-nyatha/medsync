
package com.medsync.patient.dto;

import java.time.LocalDateTime;

import com.medsync.patient.entity.Diagnosis;

public class DiagnosisResponse {

	private Long id;

	private Long patientId;

	private String diagnosisName;

	private String description;

	private Long diagnosedByAuthUserId;

	private LocalDateTime diagnosedAt;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	public DiagnosisResponse() {
	}

	public DiagnosisResponse(Long id, Long patientId, String diagnosisName, String description,
			Long diagnosedByAuthUserId, LocalDateTime diagnosedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {

		this.id = id;
		this.patientId = patientId;
		this.diagnosisName = diagnosisName;
		this.description = description;
		this.diagnosedByAuthUserId = diagnosedByAuthUserId;
		this.diagnosedAt = diagnosedAt;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	// Convert Diagnosis entity into API response DTO
	public static DiagnosisResponse from(Diagnosis diagnosis) {

		return new DiagnosisResponse(diagnosis.getId(), diagnosis.getPatient().getId(), diagnosis.getDiagnosisName(),
				diagnosis.getDescription(), diagnosis.getDiagnosedByAuthUserId(), diagnosis.getDiagnosedAt(),
				diagnosis.getCreatedAt(), diagnosis.getUpdatedAt());
	}

	public Long getId() {
		return id;
	}

	public Long getPatientId() {
		return patientId;
	}

	public String getDiagnosisName() {
		return diagnosisName;
	}

	public String getDescription() {
		return description;
	}

	public Long getDiagnosedByAuthUserId() {
		return diagnosedByAuthUserId;
	}

	public LocalDateTime getDiagnosedAt() {
		return diagnosedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
