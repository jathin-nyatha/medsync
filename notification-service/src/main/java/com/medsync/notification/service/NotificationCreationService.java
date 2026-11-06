package com.medsync.notification.service;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.medsync.notification.dto.CreateNotificationRequest;
import com.medsync.notification.dto.NotificationResponse;
import com.medsync.notification.dto.RenderedEmail;
import com.medsync.notification.entity.Notification;
import com.medsync.notification.exception.IdempotencyConflictException;
import com.medsync.notification.repository.NotificationRepository;

@Service
public class NotificationCreationService {

	private final NotificationRepository notificationRepository;
	private final NotificationPersistenceService persistenceService;
	private final EmailTemplateService emailTemplateService;

	public NotificationCreationService(NotificationRepository notificationRepository,
			NotificationPersistenceService persistenceService, EmailTemplateService emailTemplateService) {

		this.notificationRepository = notificationRepository;
		this.persistenceService = persistenceService;
		this.emailTemplateService = emailTemplateService;
	}

	public CreationResult create(CreateNotificationRequest request) {

		RenderedEmail renderedEmail = emailTemplateService.render(request.type(), request.data());

		Notification notification = new Notification(request.idempotencyKey(), renderedEmail.recipientEmail(),
				renderedEmail.subject(), request.type(), renderedEmail.plainTextContent(), renderedEmail.htmlContent());

		Optional<Notification> existing = notificationRepository.findByIdempotencyKey(request.idempotencyKey());

		if (existing.isPresent()) {
			return resolveDuplicate(existing.get(), notification);
		}

		try {
			Notification saved = persistenceService.save(notification);
			return new CreationResult(NotificationResponse.from(saved), true);

		} catch (DataIntegrityViolationException exception) {

			/*
			 * A concurrent request may have inserted the same key after our initial lookup.
			 *
			 * The persistence transaction has rolled back before execution reaches this
			 * point.
			 */
			Optional<Notification> duplicate = notificationRepository.findByIdempotencyKey(request.idempotencyKey());

			if (duplicate.isEmpty()) {
				throw exception;
			}

			return resolveDuplicate(duplicate.get(), notification);
		}
	}

	private CreationResult resolveDuplicate(Notification existing, Notification requested) {

		boolean sameRequest = existing.getRecipientEmail().equals(requested.getRecipientEmail())
				&& existing.getSubject().equals(requested.getSubject())
				&& existing.getTemplateType() == requested.getTemplateType()
				&& existing.getPlainTextContent().equals(requested.getPlainTextContent())
				&& existing.getHtmlContent().equals(requested.getHtmlContent());

		if (!sameRequest) {
			throw new IdempotencyConflictException(requested.getIdempotencyKey());
		}

		return new CreationResult(NotificationResponse.from(existing), false);
	}

	public record CreationResult(NotificationResponse notification, boolean created) {
	}
}