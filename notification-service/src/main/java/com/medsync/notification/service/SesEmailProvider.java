
package com.medsync.notification.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.medsync.notification.config.SesProperties;
import com.medsync.notification.dto.EmailMessage;
import com.medsync.notification.exception.EmailProviderException;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

@Service
@ConditionalOnProperty(prefix = "medsync.notification.ses", name = "enabled", havingValue = "true")
public class SesEmailProvider implements EmailProvider {

	private static final String UTF_8 = "UTF-8";

	private final SesV2Client sesV2Client;
	private final SesProperties properties;

	public SesEmailProvider(SesV2Client sesV2Client, SesProperties properties) {

		this.sesV2Client = sesV2Client;
		this.properties = properties;
	}

	@Override
	public EmailDeliveryResult send(EmailMessage emailMessage) {

		validateEmailMessage(emailMessage);

		try {
			Body.Builder bodyBuilder = Body.builder();

			if (hasText(emailMessage.plainTextContent())) {
				bodyBuilder.text(createContent(emailMessage.plainTextContent()));
			}

			if (hasText(emailMessage.htmlContent())) {
				bodyBuilder.html(createContent(emailMessage.htmlContent()));
			}

			Message message = Message.builder().subject(createContent(emailMessage.subject())).body(bodyBuilder.build())
					.build();

			software.amazon.awssdk.services.sesv2.model.Destination destination = software.amazon.awssdk.services.sesv2.model.Destination
					.builder().toAddresses(emailMessage.to()).build();

			EmailContent emailContent = EmailContent.builder().simple(message).build();

			SendEmailRequest request = SendEmailRequest.builder().fromEmailAddress(properties.fromEmail())
					.destination(destination).content(emailContent).build();

			SendEmailResponse response = sesV2Client.sendEmail(request);

			return new EmailDeliveryResult(response.sdkHttpResponse().statusCode(), response.messageId());

		} catch (SdkException exception) {
			throw new EmailProviderException("Unable to send email through Amazon SES.", exception);
		}
	}

	private software.amazon.awssdk.services.sesv2.model.Content createContent(String value) {

		return software.amazon.awssdk.services.sesv2.model.Content.builder().data(value).charset(UTF_8).build();
	}

	private void validateEmailMessage(EmailMessage emailMessage) {

		if (emailMessage == null) {
			throw new IllegalArgumentException("Email message must not be null.");
		}

		if (!hasText(emailMessage.to())) {
			throw new IllegalArgumentException("Recipient email address is required.");
		}

		if (!hasText(emailMessage.subject())) {
			throw new IllegalArgumentException("Email subject is required.");
		}

		boolean hasPlainText = hasText(emailMessage.plainTextContent());
		boolean hasHtml = hasText(emailMessage.htmlContent());

		if (!hasPlainText && !hasHtml) {
			throw new IllegalArgumentException("Email must contain plain-text or HTML content.");
		}
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
