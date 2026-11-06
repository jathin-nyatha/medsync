package com.medsync.notification.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medsync.notification.dto.NotificationResponse;
import com.medsync.notification.entity.Notification;
import com.medsync.notification.entity.NotificationStatus;
import com.medsync.notification.exception.NotificationNotFoundException;
import com.medsync.notification.repository.NotificationRepository;

@Service
public class NotificationQueryService {

	private final NotificationRepository notificationRepository;

	public NotificationQueryService(NotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	@Transactional(readOnly = true)
	public NotificationResponse getById(Long notificationId) {

		Notification notification = notificationRepository.findById(notificationId)
				.orElseThrow(() -> new NotificationNotFoundException(notificationId));

		return NotificationResponse.from(notification);
	}

	@Transactional(readOnly = true)
	public Page<NotificationResponse> getAll(NotificationStatus status, Pageable pageable) {

		Page<Notification> notifications;

		if (status == null) {
			notifications = notificationRepository.findAll(pageable);
		} else {
			notifications = notificationRepository.findByStatus(status, pageable);
		}

		return notifications.map(NotificationResponse::from);
	}
}