package com.medsync.auth.dto;

import com.medsync.auth.entity.Role;

public class LoginResponse {

    private String accessToken;
    private String tokenType;
    private String email;
    private Role role;
    private String message;

    public LoginResponse() {
    }

    public LoginResponse(
            String accessToken,
            String tokenType,
            String email,
            Role role,
            String message) {

        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.email = email;
        this.role = role;
        this.message = message;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public String getMessage() {
        return message;
    }
}