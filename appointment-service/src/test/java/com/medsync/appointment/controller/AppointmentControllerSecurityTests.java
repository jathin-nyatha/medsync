package com.medsync.appointment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medsync.appointment.service.AppointmentService;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentControllerSecurityTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AppointmentService appointmentService;

	// --------------------------------------------------
	// Authentication tests
	// --------------------------------------------------

	@Test
	void unauthenticatedUserCannotAccessAppointments() throws Exception {

		mockMvc.perform(get("/api/appointments/1001")).andExpect(status().isUnauthorized());

		verifyNoInteractions(appointmentService);
	}

	// --------------------------------------------------
	// AppointmentController role authorization
	// --------------------------------------------------

	@Test
	void receptionistCannotUpdateAppointmentStatus() throws Exception {

		mockMvc.perform(patch("/api/appointments/1001/status").param("status", "CONFIRMED")
				.with(jwt().jwt(token -> token.claim("userId", 10L).claim("role", "RECEPTIONIST"))
						.authorities(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"))))
				.andExpect(status().isForbidden());

		verifyNoInteractions(appointmentService);
	}

	@Test
	void doctorCannotViewAnotherDoctorsAppointments() throws Exception {

		mockMvc.perform(get("/api/appointments/doctor/99")
				.with(jwt().jwt(token -> token.claim("userId", 25L).claim("role", "DOCTOR"))
						.authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
				.andExpect(status().isForbidden());

		verify(appointmentService, never()).getDoctorAppointments(eq(99L), any());
	}

	@Test
	void doctorCanViewOwnAppointments() throws Exception {

		when(appointmentService.getDoctorAppointments(eq(25L), any())).thenReturn(Page.empty());

		mockMvc.perform(get("/api/appointments/doctor/25")
				.with(jwt().jwt(token -> token.claim("userId", 25L).claim("role", "DOCTOR"))
						.authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
				.andExpect(status().isOk());

		verify(appointmentService).getDoctorAppointments(eq(25L), any());
	}

	@Test
	void doctorCanViewOwnAppointment() throws Exception {

		mockMvc.perform(get("/api/appointments/1001")
				.with(jwt().jwt(token -> token.claim("userId", 25L).claim("role", "DOCTOR"))
						.authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
				.andExpect(status().isOk());

		verify(appointmentService).getDoctorAppointment(1001L, 25L);
	}

	@Test
	void doctorCannotRescheduleAppointment() throws Exception {

		mockMvc.perform(
				patch("/api/appointments/1001/reschedule").contentType("application/json").content("""
						{
						    "startAt": "2027-01-15T10:00:00Z",
						    "endAt": "2027-01-15T10:30:00Z"
						}
						""")
						.with(jwt().jwt(token -> token.claim("userId", 25L).claim("role", "DOCTOR"))
								.authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
				.andExpect(status().isForbidden());

		verifyNoInteractions(appointmentService);
	}

	@Test
	void adminCanViewAnyAppointment() throws Exception {

		mockMvc.perform(
				get("/api/appointments/1001").with(jwt().jwt(token -> token.claim("userId", 1L).claim("role", "ADMIN"))
						.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isOk());

		verify(appointmentService).getAppointmentById(1001L);
	}

	// --------------------------------------------------
	// InternalAppointmentController authorization
	// --------------------------------------------------

	@Test
	void nonDoctorCannotCheckInternalAssignment() throws Exception {

		mockMvc.perform(
				get("/api/internal/assignments/active").param("patientId", "101").param("doctorAuthUserId", "25")
						.with(jwt().jwt(token -> token.claim("userId", 1L).claim("role", "ADMIN"))
								.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isForbidden());

		verifyNoInteractions(appointmentService);
	}

	@Test
	void doctorCannotCheckAnotherDoctorsAssignment() throws Exception {

		mockMvc.perform(
				get("/api/internal/assignments/active").param("patientId", "101").param("doctorAuthUserId", "99")
						.with(jwt().jwt(token -> token.claim("userId", 25L).claim("role", "DOCTOR"))
								.authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
				.andExpect(status().isForbidden());

		verify(appointmentService, never()).hasActiveDoctorPatientAssignment(any(), any());
	}

	@Test
	void doctorCanCheckOwnPatientAssignment() throws Exception {

		when(appointmentService.hasActiveDoctorPatientAssignment(101L, 25L)).thenReturn(true);

		mockMvc.perform(
				get("/api/internal/assignments/active").param("patientId", "101").param("doctorAuthUserId", "25")
						.with(jwt().jwt(token -> token.claim("userId", 25L).claim("role", "DOCTOR"))
								.authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
				.andExpect(status().isOk()).andExpect(content().json("""
						{
						    "active": true
						}
						"""));

		verify(appointmentService).hasActiveDoctorPatientAssignment(101L, 25L);
	}
}