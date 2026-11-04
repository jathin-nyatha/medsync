
package com.medsync.doctor.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "doctors", uniqueConstraints = {
		@UniqueConstraint(name = "uk_doctor_auth_user_id", columnNames = "auth_user_id"),
		@UniqueConstraint(name = "uk_doctor_license_number", columnNames = "license_number") })
public class Doctor {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "auth_user_id", nullable = false, unique = true)
	private Long authUserId;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(name = "specialization", nullable = false, length = 100)
	private String specialization;

	@Column(name = "license_number", nullable = false, unique = true, length = 100)
	private String licenseNumber;

	@Column(name = "phone_number", nullable = false, length = 20)
	private String phoneNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private DoctorStatus status = DoctorStatus.ACTIVE;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	// JPA requires a no-argument constructor
	protected Doctor() {
	}

	// Constructor for creating a new doctor
	public Doctor(Long authUserId, String firstName, String lastName, String specialization, String licenseNumber,
			String phoneNumber) {

		this.authUserId = authUserId;
		this.firstName = firstName;
		this.lastName = lastName;
		this.specialization = specialization;
		this.licenseNumber = licenseNumber;
		this.phoneNumber = phoneNumber;
		this.status = DoctorStatus.ACTIVE;
	}

	// Set timestamps before inserting a new record
	@PrePersist
	protected void onCreate() {

		LocalDateTime now = LocalDateTime.now();

		this.createdAt = now;
		this.updatedAt = now;
	}

	// Update the modification timestamp before updating
	@PreUpdate
	protected void onUpdate() {

		this.updatedAt = LocalDateTime.now();
	}

	// Getters and setters

	public Long getId() {
		return id;
	}

	public Long getAuthUserId() {
		return authUserId;
	}

	public void setAuthUserId(Long authUserId) {
		this.authUserId = authUserId;
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

	public DoctorStatus getStatus() {
		return status;
	}

	public void setStatus(DoctorStatus status) {
		this.status = status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
