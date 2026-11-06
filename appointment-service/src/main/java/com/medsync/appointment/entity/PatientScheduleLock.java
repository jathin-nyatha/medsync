package com.medsync.appointment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "patient_schedule_locks")
@Getter
@NoArgsConstructor
public class PatientScheduleLock {

	@Id
	@Column(name = "patient_id")
	private Long patientId;

	public PatientScheduleLock(Long patientId) {
		this.patientId = patientId;
	}
}