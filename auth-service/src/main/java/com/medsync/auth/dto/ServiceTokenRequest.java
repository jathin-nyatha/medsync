package com.medsync.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ServiceTokenRequest(@NotBlank(message = "Client ID is required") String clientId,

		@NotBlank(message = "Client secret is required") String clientSecret) {
}