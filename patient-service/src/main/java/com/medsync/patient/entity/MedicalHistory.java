package com.medsync.patient.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "patient_medical_history")
public class MedicalHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "patient_id", nullable = false)
	private Patient patient;

	@Column(name = "condition_name", nullable = false, length = 150)
	private String conditionName;

	@Column(nullable = false, length = 2000)
	private String description;

	@Column(name = "onset_date")
	private LocalDate onsetDate;

	@Column(name = "treatment_details", length = 2000)
	private String treatmentDetails;

	@Column(name = "recorded_by_auth_user_id", nullable = false)
	private Long recordedByAuthUserId;

	@Column(name = "recorded_at", nullable = false, updatable = false)
	private LocalDateTime recordedAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected MedicalHistory() {
		// Required by JPA
	}

	public MedicalHistory(Patient patient, String conditionName, String description, LocalDate onsetDate,
			String treatmentDetails, Long recordedByAuthUserId) {

		this.patient = patient;
		this.conditionName = conditionName;
		this.description = description;
		this.onsetDate = onsetDate;
		this.treatmentDetails = treatmentDetails;
		this.recordedByAuthUserId = recordedByAuthUserId;
	}

	@PrePersist
	protected void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.recordedAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public Patient getPatient() {
		return patient;
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

	public LocalDateTime getRecordedAt() {
		return recordedAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}