
package com.medsync.appointment.client.dto;

public record ServiceTokenResponse(String accessToken, String tokenType, long expiresIn, String scope) {
}
