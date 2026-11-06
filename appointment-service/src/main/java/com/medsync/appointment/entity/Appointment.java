package com.medsync.appointment.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "appointments", indexes = {
		@Index(name = "idx_appointment_doctor_schedule", columnList = "doctor_auth_user_id,start_at,end_at,status"),
		@Index(name = "idx_appointment_patient_schedule", columnList = "patient_id,start_at,end_at,status"),
		@Index(name = "idx_appointment_doctor_patient_status", columnList = "doctor_auth_user_id,patient_id,status") })
@Getter
@Setter
@NoArgsConstructor
public class Appointment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "patient_id", nullable = false)
	private Long patientId;

	@Column(name = "doctor_auth_user_id", nullable = false)
	private Long doctorAuthUserId;

	@Column(name = "created_by_auth_user_id", nullable = false)
	private Long createdByAuthUserId;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(name = "end_at", nullable = false)
	private Instant endAt;

	@Column(name = "reason", nullable = false, length = 500)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private AppointmentStatus status;

	@Column(name = "cancellation_reason", length = 500)
	private String cancellationReason;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * Optimistic locking protects against concurrent updates to the same
	 * appointment record.
	 */
	@Version
	@Column(name = "version")
	private Long version;

	@PrePersist
	protected void onCreate() {

		if (status == null) {
			status = AppointmentStatus.SCHEDULED;
		}
	}
}