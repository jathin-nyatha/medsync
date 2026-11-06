CREATE TABLE appointments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cancellation_reason VARCHAR(500) DEFAULT NULL,
    cancelled_at DATETIME(6) DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by_auth_user_id BIGINT NOT NULL,
    doctor_auth_user_id BIGINT NOT NULL,
    end_at DATETIME(6) NOT NULL,
    patient_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    status ENUM(
        'CANCELLED',
        'COMPLETED',
        'CONFIRMED',
        'NO_SHOW',
        'SCHEDULED'
    ) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,

    PRIMARY KEY (id),

    KEY idx_appointment_doctor_schedule (
        doctor_auth_user_id,
        start_at,
        end_at,
        status
    ),

    KEY idx_appointment_patient_schedule (
        patient_id,
        start_at,
        end_at,
        status
    ),

    KEY idx_appointment_doctor_patient_status (
        doctor_auth_user_id,
        patient_id,
        status
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE doctor_schedule_locks (
    doctor_auth_user_id BIGINT NOT NULL,
    PRIMARY KEY (doctor_auth_user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE patient_schedule_locks (
    patient_id BIGINT NOT NULL,
    PRIMARY KEY (patient_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL,
    attempts INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    event_type ENUM(
        'APPOINTMENT_CANCELLED',
        'APPOINTMENT_CONFIRMATION',
        'APPOINTMENT_RESCHEDULED'
    ) NOT NULL,
    last_error VARCHAR(2000) DEFAULT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    payload LONGTEXT NOT NULL,
    processed_at DATETIME(6) DEFAULT NULL,
    status ENUM(
        'FAILED',
        'PENDING',
        'PROCESSING',
        'PUBLISHED'
    ) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    locked_until DATETIME(6) DEFAULT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_outbox_event_id UNIQUE (event_id),

    KEY idx_outbox_status_next_attempt (
        status,
        next_attempt_at
    ),

    KEY idx_outbox_created_at (
        created_at
    ),

    KEY idx_outbox_status_locked_until (
        status,
        locked_until
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;