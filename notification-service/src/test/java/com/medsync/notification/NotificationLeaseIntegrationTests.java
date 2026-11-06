
package com.medsync.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import com.medsync.notification.dto.AppointmentEmailType;
import com.medsync.notification.entity.Notification;
import com.medsync.notification.entity.NotificationStatus;
import com.medsync.notification.repository.NotificationAttemptRepository;
import com.medsync.notification.repository.NotificationRepository;
import com.medsync.notification.service.NotificationClaimService;

@SpringBootTest(properties = { "spring.datasource.url=jdbc:mysql://localhost:3306/medsync_notification_test",
		"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
		"medsync.notification.ses.enabled=false" })
@AutoConfigureMockMvc
class NotificationLeaseIntegrationTests {

	@Autowired
	private NotificationRepository notificationRepository;

	@Autowired
	private NotificationAttemptRepository notificationAttemptRepository;

	@Autowired
	private NotificationClaimService claimService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		notificationAttemptRepository.deleteAll();
		notificationRepository.deleteAll();
	}

	@Test
	void shouldClaimNewPendingNotification() {

		Notification notification = createNotification("lease-pending-001");
		Instant now = Instant.now();

		Optional<Notification> result = claimService.claim(notification.getId(), now);

		assertThat(result).isPresent();

		Notification claimed = result.orElseThrow();

		assertThat(claimed.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
		assertThat(claimed.getAttemptCount()).isEqualTo(1);
		assertThat(claimed.getLockedUntil()).isAfter(now);
		assertThat(claimed.getNextAttemptAt()).isNull();
	}

	@Test
	void shouldNotClaimNotificationWhileItsLeaseIsActive() {

		Notification notification = createNotification("lease-active-001");
		Instant firstClaimTime = Instant.now();

		Optional<Notification> firstClaim = claimService.claim(notification.getId(), firstClaimTime);

		assertThat(firstClaim).isPresent();

		Optional<Notification> secondClaim = claimService.claim(notification.getId(), Instant.now());

		assertThat(secondClaim).isEmpty();

		Notification persisted = notificationRepository.findById(notification.getId()).orElseThrow();

		assertThat(persisted.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
		assertThat(persisted.getAttemptCount()).isEqualTo(1);
	}

	@Test
	void shouldReclaimNotificationAfterProcessingLeaseExpires() {

		Notification notification = createNotification("lease-expired-001");

		Instant firstClaimTime = Instant.now();

		Optional<Notification> firstClaim = claimService.claim(notification.getId(), firstClaimTime);

		assertThat(firstClaim).isPresent();
		assertThat(firstClaim.orElseThrow().getAttemptCount()).isEqualTo(1);

		// Use the MySQL clock to avoid Java/MySQL timestamp conversion issues.
		jdbcTemplate.update("""
				UPDATE notifications
				SET locked_until = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 10 MINUTE)
				WHERE id = ?
				""", notification.getId());

		Instant recoveryTime = Instant.now();

		List<Long> eligibleIds = notificationRepository.findEligibleNotificationIds(recoveryTime, 25);

		assertThat(eligibleIds).contains(notification.getId());

		Optional<Notification> recoveredClaim = claimService.claim(notification.getId(), recoveryTime);

		assertThat(recoveredClaim).isPresent();

		Notification recovered = recoveredClaim.orElseThrow();

		assertThat(recovered.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
		assertThat(recovered.getAttemptCount()).isEqualTo(2);
		assertThat(recovered.getLockedUntil()).isAfter(recoveryTime);
	}

	@Test
	void shouldNotAllowTwoWorkersToClaimSameNotificationConcurrently() throws Exception {

		Notification notification = createNotification("lease-concurrent-001");

		int numberOfWorkers = 2;

		ExecutorService executor = Executors.newFixedThreadPool(numberOfWorkers);

		CountDownLatch ready = new CountDownLatch(numberOfWorkers);
		CountDownLatch start = new CountDownLatch(1);

		Instant claimTime = Instant.now();

		try {
			Future<Optional<Notification>> firstWorker = executor.submit(() -> {
				ready.countDown();
				start.await();

				return claimService.claim(notification.getId(), claimTime);
			});

			Future<Optional<Notification>> secondWorker = executor.submit(() -> {
				ready.countDown();
				start.await();

				return claimService.claim(notification.getId(), claimTime);
			});

			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

			start.countDown();

			Optional<Notification> firstResult = firstWorker.get(20, TimeUnit.SECONDS);

			Optional<Notification> secondResult = secondWorker.get(20, TimeUnit.SECONDS);

			int successfulClaims = (firstResult.isPresent() ? 1 : 0) + (secondResult.isPresent() ? 1 : 0);

			assertThat(successfulClaims).isEqualTo(1);

			Notification persisted = notificationRepository.findById(notification.getId()).orElseThrow();

			assertThat(persisted.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
			assertThat(persisted.getAttemptCount()).isEqualTo(1);

		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	private Notification createNotification(String idempotencyKey) {

		Notification notification = new Notification(idempotencyKey, "patient@example.com",
				"MedSync - Appointment Confirmed", AppointmentEmailType.CONFIRMATION,
				"Your appointment has been confirmed.", "<p>Your appointment has been confirmed.</p>");

		return notificationRepository.saveAndFlush(notification);
	}
}
