package com.medsync.auth.dto;

import com.medsync.auth.entity.Role;

public class RegisterResponse {

    private Long id;
    private String email;
    private Role role;

    public RegisterResponse() {
    }

    public RegisterResponse(Long id, String email, Role role) {
        this.id = id;
        this.email = email;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }
}