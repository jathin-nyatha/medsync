package com.medsync.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.medsync.notification.dto.AppointmentEmailData;
import com.medsync.notification.dto.AppointmentEmailType;
import com.medsync.notification.dto.RenderedEmail;
import com.medsync.notification.service.EmailTemplateService;

class EmailTemplateServiceTests {

	private EmailTemplateService emailTemplateService;

	@BeforeEach
	void setUp() {

		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();

		resolver.setPrefix("templates/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode("HTML");
		resolver.setCharacterEncoding("UTF-8");
		resolver.setCacheable(false);

		SpringTemplateEngine templateEngine = new SpringTemplateEngine();
		templateEngine.setTemplateResolver(resolver);

		emailTemplateService = new EmailTemplateService(templateEngine);
	}

	@Test
	void shouldRenderAppointmentConfirmationEmail() {

		AppointmentEmailData data = createAppointmentEmailData();

		RenderedEmail result = emailTemplateService.render(AppointmentEmailType.CONFIRMATION, data);

		assertThat(result.recipientEmail()).isEqualTo("patient@example.com");

		assertThat(result.subject()).isEqualTo("MedSync - Appointment Confirmed");

		assertThat(result.htmlContent()).contains("Appointment Confirmed").contains("Dr. Sharma")
				.contains("MED-APT-1001");

		assertThat(result.plainTextContent()).contains("successfully scheduled").contains("MED-APT-1001");
	}

	@Test
	void shouldRenderAppointmentRescheduledEmail() {

		RenderedEmail result = emailTemplateService.render(AppointmentEmailType.RESCHEDULED,
				createAppointmentEmailData());

		assertThat(result.subject()).isEqualTo("MedSync - Appointment Rescheduled");

		assertThat(result.htmlContent()).contains("Appointment Rescheduled");
	}

	@Test
	void shouldRenderAppointmentCancellationEmail() {

		AppointmentEmailData data = createAppointmentEmailData();

		RenderedEmail result = emailTemplateService.render(AppointmentEmailType.CANCELLATION, data);

		assertThat(result.subject()).isEqualTo("MedSync - Appointment Cancelled");

		assertThat(result.htmlContent()).contains("Appointment Cancelled");

		assertThat(result.plainTextContent()).contains("appointment has been cancelled");
	}

	@Test
	void shouldRenderAppointmentReminderEmail() {

		RenderedEmail result = emailTemplateService.render(AppointmentEmailType.REMINDER, createAppointmentEmailData());

		assertThat(result.subject()).isEqualTo("MedSync - Appointment Reminder");

		assertThat(result.htmlContent()).contains("Appointment Reminder");
	}

	@Test
	void shouldRejectMissingRecipientEmail() {

		AppointmentEmailData data = new AppointmentEmailData("", "Rahul Kumar", "Dr. Sharma",
				"10 November 2026, 10:30 AM", "General Consultation", "MED-APT-1001", null);

		assertThatThrownBy(() -> emailTemplateService.render(AppointmentEmailType.CONFIRMATION, data))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Recipient email");
	}

	@Test
	void shouldEscapeUntrustedHtmlInPatientName() {

		AppointmentEmailData data = new AppointmentEmailData("patient@example.com", "<script>alert('xss')</script>",
				"Dr. Sharma", "10 November 2026, 10:30 AM", "General Consultation", "MED-APT-1001", null);

		RenderedEmail result = emailTemplateService.render(AppointmentEmailType.CONFIRMATION, data);

		assertThat(result.htmlContent()).doesNotContain("<script>alert('xss')</script>").contains("&lt;script&gt;");
	}

	private AppointmentEmailData createAppointmentEmailData() {

		return new AppointmentEmailData("patient@example.com", "Rahul Kumar", "Dr. Sharma",
				"10 November 2026, 10:30 AM", "General Consultation", "MED-APT-1001", null);
	}
}