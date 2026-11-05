
package com.medsync.patient.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.medsync.patient.entity.BloodGroup;
import com.medsync.patient.entity.Gender;
import com.medsync.patient.entity.Patient;

public class PatientResponse {

	private Long id;
	private Long authUserId;
	private String firstName;
	private String lastName;
	private LocalDate dateOfBirth;
	private Gender gender;
	private String phoneNumber;
	private String address;
	private BloodGroup bloodGroup;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	public static PatientResponse from(Patient patient) {

		PatientResponse response = new PatientResponse();

		response.id = patient.getId();
		response.authUserId = patient.getAuthUserId();
		response.firstName = patient.getFirstName();
		response.lastName = patient.getLastName();
		response.dateOfBirth = patient.getDateOfBirth();
		response.gender = patient.getGender();
		response.phoneNumber = patient.getPhoneNumber();
		response.address = patient.getAddress();
		response.bloodGroup = patient.getBloodGroup();
		response.createdAt = patient.getCreatedAt();
		response.updatedAt = patient.getUpdatedAt();

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

	public LocalDate getDateOfBirth() {
		return dateOfBirth;
	}

	public Gender getGender() {
		return gender;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public String getAddress() {
		return address;
	}

	public BloodGroup getBloodGroup() {
		return bloodGroup;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
