package com.medsync.notification.service;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import com.medsync.notification.dto.AppointmentEmailData;
import com.medsync.notification.dto.AppointmentEmailType;
import com.medsync.notification.dto.RenderedEmail;

@Service
public class EmailTemplateService {

	private final SpringTemplateEngine templateEngine;

	private final Map<AppointmentEmailType, String> templateNames = new EnumMap<>(AppointmentEmailType.class);

	private final Map<AppointmentEmailType, String> subjects = new EnumMap<>(AppointmentEmailType.class);

	public EmailTemplateService(SpringTemplateEngine templateEngine) {

		this.templateEngine = templateEngine;

		templateNames.put(AppointmentEmailType.CONFIRMATION, "email/appointment-confirmation");

		templateNames.put(AppointmentEmailType.RESCHEDULED, "email/appointment-rescheduled");

		templateNames.put(AppointmentEmailType.CANCELLATION, "email/appointment-cancellation");

		templateNames.put(AppointmentEmailType.REMINDER, "email/appointment-reminder");

		subjects.put(AppointmentEmailType.CONFIRMATION, "MedSync - Appointment Confirmed");

		subjects.put(AppointmentEmailType.RESCHEDULED, "MedSync - Appointment Rescheduled");

		subjects.put(AppointmentEmailType.CANCELLATION, "MedSync - Appointment Cancelled");

		subjects.put(AppointmentEmailType.REMINDER, "MedSync - Appointment Reminder");
	}

	public RenderedEmail render(AppointmentEmailType emailType, AppointmentEmailData data) {

		validate(emailType, data);

		Context context = new Context();

		context.setVariable("patientName", data.patientName());
		context.setVariable("doctorName", data.doctorName());
		context.setVariable("appointmentDateTime", data.appointmentDateTime());
		context.setVariable("reason", data.reason());
		context.setVariable("appointmentReference", data.appointmentReference());
		context.setVariable("cancellationReason", data.cancellationReason());

		String htmlContent = templateEngine.process(templateNames.get(emailType), context);

		String plainTextContent = createPlainTextContent(emailType, data);

		return new RenderedEmail(data.recipientEmail(), subjects.get(emailType), plainTextContent, htmlContent);
	}

	private void validate(AppointmentEmailType emailType, AppointmentEmailData data) {

		if (emailType == null) {
			throw new IllegalArgumentException("Appointment email type is required.");
		}

		if (data == null) {
			throw new IllegalArgumentException("Appointment email data is required.");
		}

		requireText(data.recipientEmail(), "Recipient email");
		requireText(data.patientName(), "Patient name");
		requireText(data.doctorName(), "Doctor name");
		requireText(data.appointmentDateTime(), "Appointment date and time");
		requireText(data.reason(), "Appointment reason");
		requireText(data.appointmentReference(), "Appointment reference");

		if (emailType == AppointmentEmailType.CANCELLATION
				&& (data.cancellationReason() == null || data.cancellationReason().isBlank())) {

			// A cancellation reason is optional.
		}
	}

	private void requireText(String value, String fieldName) {

		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " is required.");
		}
	}

	private String createPlainTextContent(AppointmentEmailType emailType, AppointmentEmailData data) {

		StringBuilder content = new StringBuilder();

		content.append("Hello ").append(data.patientName()).append(",\n\n");

		switch (emailType) {

		case CONFIRMATION -> content.append("Your appointment has been successfully scheduled.\n\n");

		case RESCHEDULED -> content.append("Your appointment has been rescheduled successfully.\n\n");

		case CANCELLATION -> content.append("Your appointment has been cancelled.\n\n");

		case REMINDER -> content.append("This is a reminder about your upcoming appointment.\n\n");
		}

		content.append("Doctor: ").append(data.doctorName()).append("\n");

		content.append("Date and Time: ").append(data.appointmentDateTime()).append("\n");

		content.append("Reason: ").append(data.reason()).append("\n");

		content.append("Appointment Reference: ").append(data.appointmentReference()).append("\n");

		if (emailType == AppointmentEmailType.CANCELLATION && data.cancellationReason() != null
				&& !data.cancellationReason().isBlank()) {

			content.append("Cancellation Reason: ").append(data.cancellationReason()).append("\n");
		}

		content.append("\nThank you for choosing MedSync.\n\n")
				.append("This is an automated notification. Please do not reply.");

		return content.toString();
	}
}