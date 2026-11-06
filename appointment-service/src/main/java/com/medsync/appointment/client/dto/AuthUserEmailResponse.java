
package com.medsync.appointment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthUserEmailResponse(Long userId, String email, boolean enabled) {
}
