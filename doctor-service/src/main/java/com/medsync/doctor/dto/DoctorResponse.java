
package com.medsync.doctor.dto;

import java.time.LocalDateTime;

import com.medsync.doctor.entity.Doctor;
import com.medsync.doctor.entity.DoctorStatus;

public class DoctorResponse {

	private Long id;
	private Long authUserId;
	private String firstName;
	private String lastName;
	private String specialization;
	private String licenseNumber;
	private String phoneNumber;
	private DoctorStatus status;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	public static DoctorResponse fromEntity(Doctor doctor) {

		DoctorResponse response = new DoctorResponse();

		response.id = doctor.getId();
		response.authUserId = doctor.getAuthUserId();
		response.firstName = doctor.getFirstName();
		response.lastName = doctor.getLastName();
		response.specialization = doctor.getSpecialization();
		response.licenseNumber = doctor.getLicenseNumber();
		response.phoneNumber = doctor.getPhoneNumber();
		response.status = doctor.getStatus();
		response.createdAt = doctor.getCreatedAt();
		response.updatedAt = doctor.getUpdatedAt();

		return response;
	}

	public Long getId() {
		return id;
	}

	public Long getAuthUserId() {
		return authUserId;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public String getSpecialization() {
		return specialization;
	}

	public String getLicenseNumber() {
		return licenseNumber;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public DoctorStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
