
CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(100) NOT NULL,
    event_type ENUM(
        'APPOINTMENT_CANCELLED',
        'APPOINTMENT_CONFIRMATION',
        'APPOINTMENT_RESCHEDULED'
    ) NOT NULL,
    appointment_id BIGINT NOT NULL,
    payload LONGTEXT NOT NULL,
    status ENUM(
        'FAILED',
        'PENDING',
        'PROCESSING',
        'PUBLISHED'
    ) NOT NULL,
    attempts INT NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    locked_until DATETIME(6) DEFAULT NULL,
    last_error VARCHAR(2000) DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) DEFAULT NULL,
    version BIGINT NOT NULL,

    PRIMARY KEY (id),

    UNIQUE KEY uk_outbox_event_id (event_id),

    INDEX idx_outbox_status_next_attempt (
        status,
        next_attempt_at
    ),

    INDEX idx_outbox_status_locked_until (
        status,
        locked_until
    ),

    INDEX idx_outbox_created_at (created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
