package com.medsync.notification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.medsync.notification.entity.NotificationAttempt;

public interface NotificationAttemptRepository extends JpaRepository<NotificationAttempt, Long> {

	List<NotificationAttempt> findByNotification_IdOrderByAttemptedAtDesc(Long notificationId);

	boolean existsByNotification_IdAndAttemptNumber(Long notificationId, int attemptNumber);
}