
package com.medsync.appointment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DoctorValidationResponse(Long id, Long authUserId, String status) {
}
