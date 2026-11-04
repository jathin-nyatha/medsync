
package com.medsync.auth.dto;

import com.medsync.auth.entity.Role;

public class DoctorEligibilityResponse {

	private Long userId;
	private Role role;
	private boolean enabled;
	private boolean eligible;

	public DoctorEligibilityResponse() {
	}

	public DoctorEligibilityResponse(Long userId, Role role, boolean enabled, boolean eligible) {

		this.userId = userId;
		this.role = role;
		this.enabled = enabled;
		this.eligible = eligible;
	}

	public Long getUserId() {
		return userId;
	}

	public Role getRole() {
		return role;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isEligible() {
		return eligible;
	}
}
