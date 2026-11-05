package com.medsync.patient.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.medsync.patient.entity.MedicalHistory;

public class MedicalHistoryResponse {

	private Long id;
	private Long patientId;
	private String conditionName;
	private String description;
	private LocalDate onsetDate;
	private String treatmentDetails;
	private Long recordedByAuthUserId;
	private LocalDateTime recordedAt;
	private LocalDateTime updatedAt;

	public static MedicalHistoryResponse from(MedicalHistory history) {

		MedicalHistoryResponse response = new MedicalHistoryResponse();

		response.setId(history.getId());
		response.setPatientId(history.getPatient().getId());
		response.setConditionName(history.getConditionName());
		response.setDescription(history.getDescription());
		response.setOnsetDate(history.getOnsetDate());
		response.setTreatmentDetails(history.getTreatmentDetails());
		response.setRecordedByAuthUserId(history.getRecordedByAuthUserId());
		response.setRecordedAt(history.getRecordedAt());
		response.setUpdatedAt(history.getUpdatedAt());

		return response;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getPatientId() {
		return patientId;
	}

	public void setPatientId(Long patientId) {
		this.patientId = patientId;
	}

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

	public Long getRecordedByAuthUserId() {
		return recordedByAuthUserId;
	}

	public void setRecordedByAuthUserId(Long recordedByAuthUserId) {
		this.recordedByAuthUserId = recordedByAuthUserId;
	}

	public LocalDateTime getRecordedAt() {
		return recordedAt;
	}

	public void setRecordedAt(LocalDateTime recordedAt) {
		this.recordedAt = recordedAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
}