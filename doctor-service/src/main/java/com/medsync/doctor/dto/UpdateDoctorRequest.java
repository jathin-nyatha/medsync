package com.medsync.doctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateDoctorRequest {

	@NotBlank(message = "First name is required")
	@Size(max = 100, message = "First name cannot exceed 100 characters")
	private String firstName;

	@NotBlank(message = "Last name is required")
	@Size(max = 100, message = "Last name cannot exceed 100 characters")
	private String lastName;

	@NotBlank(message = "Specialization is required")
	@Size(max = 100, message = "Specialization cannot exceed 100 characters")
	private String specialization;

	@NotBlank(message = "License number is required")
	@Size(max = 100, message = "License number cannot exceed 100 characters")
	private String licenseNumber;

	@NotBlank(message = "Phone number is required")
	@Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Phone number must contain 10 to 15 digits, optionally prefixed with +")
	private String phoneNumber;

	public UpdateDoctorRequest() {
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getSpecialization() {
		return specialization;
	}

	public void setSpecialization(String specialization) {
		this.specialization = specialization;
	}

	public String getLicenseNumber() {
		return licenseNumber;
	}

	public void setLicenseNumber(String licenseNumber) {
		this.licenseNumber = licenseNumber;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}
}