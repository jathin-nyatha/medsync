package com.medsync.doctor.dto;

import com.medsync.doctor.entity.DoctorStatus;

import jakarta.validation.constraints.NotNull;

public class UpdateDoctorStatusRequest {

	@NotNull(message = "Doctor status is required")
	private DoctorStatus status;

	public UpdateDoctorStatusRequest() {
	}

	public DoctorStatus getStatus() {
		return status;
	}

	public void setStatus(DoctorStatus status) {
		this.status = status;
	}
}