package com.medsync.auth.dto;

public record UserEmailResponse(Long userId, String email, boolean enabled) {
}