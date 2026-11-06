package com.medsync.appointment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "doctor_schedule_locks")
@Getter
@NoArgsConstructor
public class DoctorScheduleLock {

	@Id
	@Column(name = "doctor_auth_user_id")
	private Long doctorAuthUserId;

	public DoctorScheduleLock(Long doctorAuthUserId) {
		this.doctorAuthUserId = doctorAuthUserId;
	}
}