
package com.medsync.patient.entity;

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
@Table(name = "patient_diagnoses")
public class Diagnosis {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "patient_id", nullable = false)
	private Patient patient;

	@Column(name = "diagnosis_name", nullable = false, length = 150)
	private String diagnosisName;

	@Column(length = 2000)
	private String description;

	@Column(name = "diagnosed_by_auth_user_id", nullable = false)
	private Long diagnosedByAuthUserId;

	@Column(name = "diagnosed_at", nullable = false, updatable = false)
	private LocalDateTime diagnosedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected Diagnosis() {
		// Required by JPA
	}

	public Diagnosis(Patient patient, String diagnosisName, String description, Long diagnosedByAuthUserId) {

		this.patient = patient;
		this.diagnosisName = diagnosisName;
		this.description = description;
		this.diagnosedByAuthUserId = diagnosedByAuthUserId;
	}

	@PrePersist
	protected void onCreate() {

		LocalDateTime now = LocalDateTime.now();

		this.diagnosedAt = now;
		this.createdAt = now;
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
