
package com.medsync.notification.controller;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.medsync.notification.dto.CreateNotificationRequest;
import com.medsync.notification.dto.NotificationResponse;
import com.medsync.notification.entity.NotificationStatus;
import com.medsync.notification.service.NotificationCreationService;
import com.medsync.notification.service.NotificationQueryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	private final NotificationCreationService creationService;
	private final NotificationQueryService queryService;

	public NotificationController(NotificationCreationService creationService, NotificationQueryService queryService) {

		this.creationService = creationService;
		this.queryService = queryService;
	}

	@PostMapping
	@PreAuthorize("""
			hasAnyRole(
			    'ADMIN',
			    'RECEPTIONIST',
			    'APPOINTMENT_SERVICE'
			)
			""")
	public ResponseEntity<NotificationResponse> createNotification(
			@Valid @RequestBody CreateNotificationRequest request) {

		NotificationCreationService.CreationResult result = creationService.create(request);

		if (!result.created()) {
			return ResponseEntity.ok(result.notification());
		}

		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(result.notification().id()).toUri();

		return ResponseEntity.created(location).body(result.notification());
	}

	@GetMapping("/{notificationId}")
	@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
	public ResponseEntity<NotificationResponse> getNotification(@PathVariable Long notificationId) {

		return ResponseEntity.ok(queryService.getById(notificationId));
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
	public ResponseEntity<Page<NotificationResponse>> getNotifications(
			@RequestParam(required = false) NotificationStatus status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {

		if (page < 0) {
			throw new IllegalArgumentException("Page number cannot be negative");
		}

		if (size < 1 || size > 100) {
			throw new IllegalArgumentException("Page size must be between 1 and 100");
		}

		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

		return ResponseEntity.ok(queryService.getAll(status, pageable));
	}
}
