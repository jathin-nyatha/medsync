
package com.medsync.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medsync.notification.config.SesProperties;
import com.medsync.notification.dto.EmailMessage;
import com.medsync.notification.exception.EmailProviderException;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

@ExtendWith(MockitoExtension.class)
class SesEmailProviderTests {

	private static final String FROM_EMAIL = "notifications@medsync.example";
	private static final String TO_EMAIL = "patient@example.com";
	private static final String MESSAGE_ID = "ses-message-12345";
	private static final String SUBJECT = "Appointment confirmation";
	private static final String PLAIN_TEXT = "Your appointment has been confirmed.";
	private static final String HTML_CONTENT = "<html><body>Your appointment has been confirmed.</body></html>";

	@Mock
	private SesV2Client sesV2Client;

	private SesProperties sesProperties;

	private SesEmailProvider sesEmailProvider;

	@BeforeEach
	void setUp() {
		sesProperties = new SesProperties("ap-south-1", FROM_EMAIL, true);
		sesEmailProvider = new SesEmailProvider(sesV2Client, sesProperties);
	}

	@Test
	void sendShouldSendPlainTextAndHtmlEmailSuccessfully() {
		when(sesV2Client.sendEmail(any(SendEmailRequest.class))).thenReturn(createSuccessfulResponse());

		EmailMessage emailMessage = new EmailMessage(TO_EMAIL, SUBJECT, PLAIN_TEXT, HTML_CONTENT);

		EmailProvider.EmailDeliveryResult result = sesEmailProvider.send(emailMessage);

		ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);

		verify(sesV2Client).sendEmail(requestCaptor.capture());

		SendEmailRequest request = requestCaptor.getValue();

		assertThat(request.fromEmailAddress()).isEqualTo(FROM_EMAIL);
		assertThat(request.destination().toAddresses()).containsExactly(TO_EMAIL);
		assertThat(request.content().simple().subject().data()).isEqualTo(SUBJECT);
		assertThat(request.content().simple().body().text().data()).isEqualTo(PLAIN_TEXT);
		assertThat(request.content().simple().body().html().data()).isEqualTo(HTML_CONTENT);

		assertThat(result.statusCode()).isEqualTo(200);
		assertThat(result.providerMessageId()).isEqualTo(MESSAGE_ID);
	}

	@Test
	void sendShouldSupportPlainTextOnlyEmail() {
		when(sesV2Client.sendEmail(any(SendEmailRequest.class))).thenReturn(createSuccessfulResponse());

		EmailMessage emailMessage = new EmailMessage(TO_EMAIL, SUBJECT, PLAIN_TEXT, null);

		sesEmailProvider.send(emailMessage);

		ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);

		verify(sesV2Client).sendEmail(requestCaptor.capture());

		var body = requestCaptor.getValue().content().simple().body();

		assertThat(body.text()).isNotNull();
		assertThat(body.text().data()).isEqualTo(PLAIN_TEXT);
		assertThat(body.html()).isNull();
	}

	@Test
	void sendShouldSupportHtmlOnlyEmail() {
		when(sesV2Client.sendEmail(any(SendEmailRequest.class))).thenReturn(createSuccessfulResponse());

		EmailMessage emailMessage = new EmailMessage(TO_EMAIL, SUBJECT, null, HTML_CONTENT);

		sesEmailProvider.send(emailMessage);

		ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);

		verify(sesV2Client).sendEmail(requestCaptor.capture());

		var body = requestCaptor.getValue().content().simple().body();

		assertThat(body.html()).isNotNull();
		assertThat(body.html().data()).isEqualTo(HTML_CONTENT);
		assertThat(body.text()).isNull();
	}

	@Test
	void sendShouldWrapAwsSdkException() {
		SdkClientException sdkException = SdkClientException.create("Simulated SES failure");

		when(sesV2Client.sendEmail(any(SendEmailRequest.class))).thenThrow(sdkException);

		EmailMessage emailMessage = new EmailMessage(TO_EMAIL, SUBJECT, PLAIN_TEXT, HTML_CONTENT);

		EmailProviderException exception = assertThrows(EmailProviderException.class,
				() -> sesEmailProvider.send(emailMessage));

		assertThat(exception.getMessage()).isEqualTo("Unable to send email through Amazon SES.");

		assertThat(exception.getCause()).isSameAs(sdkException);
	}

	@Test
	void sendShouldRejectNullEmailMessage() {
		assertThrows(IllegalArgumentException.class, () -> sesEmailProvider.send(null));
	}

	@Test
	void sendShouldRejectBlankRecipient() {
		EmailMessage emailMessage = new EmailMessage(" ", SUBJECT, PLAIN_TEXT, HTML_CONTENT);

		assertThrows(IllegalArgumentException.class, () -> sesEmailProvider.send(emailMessage));
	}

	@Test
	void sendShouldRejectBlankSubject() {
		EmailMessage emailMessage = new EmailMessage(TO_EMAIL, " ", PLAIN_TEXT, HTML_CONTENT);

		assertThrows(IllegalArgumentException.class, () -> sesEmailProvider.send(emailMessage));
	}

	@Test
	void sendShouldRejectEmailWithoutBodyContent() {
		EmailMessage emailMessage = new EmailMessage(TO_EMAIL, SUBJECT, null, null);

		assertThrows(IllegalArgumentException.class, () -> sesEmailProvider.send(emailMessage));
	}

	private SendEmailResponse createSuccessfulResponse() {
		SendEmailResponse.Builder builder = SendEmailResponse.builder();

		builder.messageId(MESSAGE_ID);

		builder.sdkHttpResponse(SdkHttpResponse.builder().statusCode(200).build());

		return builder.build();
	}
}
