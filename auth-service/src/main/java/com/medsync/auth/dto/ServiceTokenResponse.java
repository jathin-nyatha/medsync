package com.medsync.auth.dto;

public record ServiceTokenResponse(String accessToken, String tokenType, long expiresIn, String scope) {
}