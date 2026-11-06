
CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    accepted_at DATETIME(6) DEFAULT NULL,
    attempt_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    delivered_at DATETIME(6) DEFAULT NULL,
    html_content MEDIUMTEXT,
    idempotency_key VARCHAR(100) NOT NULL,
    last_error VARCHAR(1000) DEFAULT NULL,
    next_attempt_at DATETIME(6) DEFAULT NULL,
    plain_text_content TEXT,
    provider_message_id VARCHAR(150) DEFAULT NULL,
    recipient_email VARCHAR(254) NOT NULL,
    status ENUM(
        'ACCEPTED',
        'BOUNCED',
        'DELIVERED',
        'FAILED',
        'PENDING',
        'PROCESSING'
    ) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    template_type ENUM(
        'CANCELLATION',
        'CONFIRMATION',
        'REMINDER',
        'RESCHEDULED'
    ) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    locked_until DATETIME(6) DEFAULT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_notification_idempotency_key
        UNIQUE (idempotency_key),

    INDEX idx_notification_status (status),
    INDEX idx_notification_next_attempt (next_attempt_at),
    INDEX idx_notification_provider_message (provider_message_id),
    INDEX idx_notification_status_lease (status, locked_until)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE notification_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_number INT NOT NULL,
    attempted_at DATETIME(6) NOT NULL,
    error_message VARCHAR(1000) DEFAULT NULL,
    provider_message_id VARCHAR(150) DEFAULT NULL,
    provider_status_code INT DEFAULT NULL,
    status ENUM('FAILED', 'SUCCESS') NOT NULL,
    notification_id BIGINT NOT NULL,

    PRIMARY KEY (id),

    INDEX idx_attempt_notification (notification_id),

    CONSTRAINT fk_notification_attempts_notification
        FOREIGN KEY (notification_id)
        REFERENCES notifications (id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
