package com.medsync.appointment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientValidationResponse(Long id, Long authUserId, String firstName, String lastName) {
}