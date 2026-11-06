package com.medsync.notification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medsync.notification.entity.Notification;
import com.medsync.notification.repository.NotificationRepository;

@Service
public class NotificationPersistenceService {

	private final NotificationRepository notificationRepository;

	public NotificationPersistenceService(NotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	@Transactional
	public Notification save(Notification notification) {
		return notificationRepository.saveAndFlush(notification);
	}
}