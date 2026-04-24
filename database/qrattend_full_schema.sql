-- QR Attend full MariaDB/XAMPP schema
-- Password hashes below use SHA-256 hex strings.
-- admin123  = 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9
-- teach123  = 63cb9c6fa2d65784658539a93ad47f2274a02ddff344537beb97bd399938ad22
-- faculty456 = 24e01a260fd3f036925e4438acf3c9d73a726fdf3965a1cc9548c2962b094f40

CREATE DATABASE IF NOT EXISTS qrattend_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE qrattend_db;

CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    password_hash CHAR(64) NOT NULL,
    role ENUM('ADMIN', 'TEACHER') NOT NULL,
    account_status ENUM('ACTIVE', 'INACTIVE', 'LOCKED') NOT NULL DEFAULT 'ACTIVE',
    must_change_password TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_role_status (role, account_status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS teacher_profiles (
    teacher_user_id BIGINT UNSIGNED NOT NULL,
    employee_code VARCHAR(50) NULL,
    schedule_edit_mode ENUM('APPROVAL_REQUIRED', 'DIRECT_EDIT') NOT NULL DEFAULT 'APPROVAL_REQUIRED',
    notes TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (teacher_user_id),
    UNIQUE KEY uk_teacher_profiles_employee_code (employee_code),
    CONSTRAINT fk_teacher_profiles_user
        FOREIGN KEY (teacher_user_id) REFERENCES users (user_id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS student_profiles (
    student_pk BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    student_code VARCHAR(50) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    section_name VARCHAR(80) NOT NULL,
    qr_status ENUM('QUEUED', 'SENT', 'FAILED') NOT NULL DEFAULT 'QUEUED',
    account_status ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_by_teacher_id BIGINT UNSIGNED NOT NULL,
    managed_by_admin_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (student_pk),
    UNIQUE KEY uk_student_profiles_code (student_code),
    UNIQUE KEY uk_student_profiles_email (email),
    KEY idx_student_profiles_section (section_name),
    KEY idx_student_profiles_teacher (created_by_teacher_id),
    CONSTRAINT fk_student_profiles_teacher
        FOREIGN KEY (created_by_teacher_id) REFERENCES users (user_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_student_profiles_admin
        FOREIGN KEY (managed_by_admin_id) REFERENCES users (user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS teacher_student_assignments (
    assignment_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    teacher_user_id BIGINT UNSIGNED NOT NULL,
    student_pk BIGINT UNSIGNED NOT NULL,
    assignment_status ENUM('ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (assignment_id),
    UNIQUE KEY uk_teacher_student_assignment (teacher_user_id, student_pk),
    CONSTRAINT fk_teacher_student_assignments_teacher
        FOREIGN KEY (teacher_user_id) REFERENCES users (user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_teacher_student_assignments_student
        FOREIGN KEY (student_pk) REFERENCES student_profiles (student_pk)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS teacher_schedules (
    schedule_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    teacher_user_id BIGINT UNSIGNED NOT NULL,
    subject_code VARCHAR(50) NULL,
    subject_name VARCHAR(150) NOT NULL,
    day_of_week TINYINT UNSIGNED NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    room_name VARCHAR(100) NOT NULL,
    schedule_status ENUM('APPROVED', 'INACTIVE', 'ARCHIVED') NOT NULL DEFAULT 'APPROVED',
    created_by_user_id BIGINT UNSIGNED NOT NULL,
    updated_by_user_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (schedule_id),
    KEY idx_teacher_schedules_teacher_day (teacher_user_id, day_of_week, start_time),
    CONSTRAINT chk_teacher_schedules_day_of_week CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_teacher_schedules_time_order CHECK (start_time < end_time),
    CONSTRAINT fk_teacher_schedules_teacher
        FOREIGN KEY (teacher_user_id) REFERENCES users (user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_teacher_schedules_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_teacher_schedules_updated_by
        FOREIGN KEY (updated_by_user_id) REFERENCES users (user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS schedule_change_requests (
    request_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT UNSIGNED NOT NULL,
    teacher_user_id BIGINT UNSIGNED NOT NULL,
    old_snapshot JSON NOT NULL,
    requested_subject_name VARCHAR(150) NOT NULL,
    requested_day_of_week TINYINT UNSIGNED NOT NULL,
    requested_start_time TIME NOT NULL,
    requested_end_time TIME NOT NULL,
    requested_room_name VARCHAR(100) NOT NULL,
    requested_snapshot JSON NOT NULL,
    reason TEXT NOT NULL,
    request_status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    reviewed_by_user_id BIGINT UNSIGNED NULL,
    reviewed_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (request_id),
    KEY idx_schedule_change_requests_status (request_status, teacher_user_id),
    CONSTRAINT fk_schedule_change_requests_schedule
        FOREIGN KEY (schedule_id) REFERENCES teacher_schedules (schedule_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_schedule_change_requests_teacher
        FOREIGN KEY (teacher_user_id) REFERENCES users (user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_schedule_change_requests_reviewer
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users (user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS student_roster_change_requests (
    removal_request_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    teacher_user_id BIGINT UNSIGNED NOT NULL,
    student_pk BIGINT UNSIGNED NOT NULL,
    request_type ENUM('REMOVE_FROM_LIST') NOT NULL DEFAULT 'REMOVE_FROM_LIST',
    request_status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    reason TEXT NOT NULL,
    reviewed_by_user_id BIGINT UNSIGNED NULL,
    reviewed_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (removal_request_id),
    KEY idx_student_roster_change_requests_status (request_status, teacher_user_id),
    CONSTRAINT fk_student_roster_change_requests_teacher
        FOREIGN KEY (teacher_user_id) REFERENCES users (user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_student_roster_change_requests_student
        FOREIGN KEY (student_pk) REFERENCES student_profiles (student_pk)
        ON DELETE CASCADE,
    CONSTRAINT fk_student_roster_change_requests_reviewer
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users (user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS attendance_sessions (
    session_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT UNSIGNED NULL,
    teacher_user_id BIGINT UNSIGNED NOT NULL,
    subject_name VARCHAR(150) NOT NULL,
    room_name VARCHAR(100) NOT NULL,
    session_date DATE NOT NULL,
    opened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP NULL DEFAULT NULL,
    session_state ENUM('LOCKED', 'ACTIVE', 'OVERRIDE_ACTIVE', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
    session_mode ENUM('SCHEDULE', 'OVERRIDE') NOT NULL DEFAULT 'SCHEDULE',
    override_reason TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id),
    KEY idx_attendance_sessions_teacher_date (teacher_user_id, session_date),
    CONSTRAINT fk_attendance_sessions_schedule
        FOREIGN KEY (schedule_id) REFERENCES teacher_schedules (schedule_id)
        ON DELETE SET NULL,
    CONSTRAINT fk_attendance_sessions_teacher
        FOREIGN KEY (teacher_user_id) REFERENCES users (user_id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS student_qr_tokens (
    qr_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    student_pk BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) NOT NULL,
    token_type ENUM('PERMANENT', 'ROTATING') NOT NULL DEFAULT 'PERMANENT',
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL DEFAULT NULL,
    emailed_at TIMESTAMP NULL DEFAULT NULL,
    last_used_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (qr_id),
    UNIQUE KEY uk_student_qr_tokens_hash (token_hash),
    KEY idx_student_qr_tokens_student_active (student_pk, is_active),
    CONSTRAINT fk_student_qr_tokens_student
        FOREIGN KEY (student_pk) REFERENCES student_profiles (student_pk)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS iot_devices (
    device_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_code VARCHAR(80) NOT NULL,
    device_name VARCHAR(120) NOT NULL,
    room_name VARCHAR(100) NOT NULL,
    device_type ENUM('SCANNER', 'CAMERA', 'KIOSK', 'GATEWAY') NOT NULL,
    device_status ENUM('ONLINE', 'OFFLINE', 'MAINTENANCE') NOT NULL DEFAULT 'OFFLINE',
    firmware_version VARCHAR(50) NULL,
    assigned_teacher_user_id BIGINT UNSIGNED NULL,
    last_seen_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (device_id),
    UNIQUE KEY uk_iot_devices_code (device_code),
    CONSTRAINT fk_iot_devices_teacher
        FOREIGN KEY (assigned_teacher_user_id) REFERENCES users (user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS qr_scan_logs (
    scan_log_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NULL,
    student_pk BIGINT UNSIGNED NULL,
    device_id BIGINT UNSIGNED NULL,
    token_hash CHAR(64) NULL,
    payload_preview VARCHAR(180) NOT NULL,
    scan_result ENUM('ACCEPTED', 'DUPLICATE', 'REJECTED') NOT NULL,
    rejection_reason VARCHAR(255) NULL,
    scanned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (scan_log_id),
    KEY idx_qr_scan_logs_result_time (scan_result, scanned_at),
    CONSTRAINT fk_qr_scan_logs_session
        FOREIGN KEY (session_id) REFERENCES attendance_sessions (session_id)
        ON DELETE SET NULL,
    CONSTRAINT fk_qr_scan_logs_student
        FOREIGN KEY (student_pk) REFERENCES student_profiles (student_pk)
        ON DELETE SET NULL,
    CONSTRAINT fk_qr_scan_logs_device
        FOREIGN KEY (device_id) REFERENCES iot_devices (device_id)
        ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS attendance_records (
    attendance_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NOT NULL,
    student_pk BIGINT UNSIGNED NOT NULL,
    teacher_user_id BIGINT UNSIGNED NOT NULL,
    subject_name VARCHAR(150) NOT NULL,
    attendance_source ENUM('QR', 'MANUAL') NOT NULL,
    attendance_status ENUM('PRESENT', 'LATE', 'ABSENT') NOT NULL DEFAULT 'PRESENT',
    note TEXT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (attendance_id),
    UNIQUE KEY uk_attendance_records_once_per_session (session_id, student_pk),
    KEY idx_attendance_records_teacher_time (teacher_user_id, recorded_at),
    CONSTRAINT fk_attendance_records_session
        FOREIGN KEY (session_id) REFERENCES attendance_sessions (session_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_attendance_records_student
        FOREIGN KEY (student_pk) REFERENCES student_profiles (student_pk)
        ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_records_teacher
        FOREIGN KEY (teacher_user_id) REFERENCES users (user_id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS email_dispatch_logs (
    email_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    recipient_email VARCHAR(150) NOT NULL,
    email_type ENUM('TEACHER_PASSWORD', 'STUDENT_QR', 'PASSWORD_RESET', 'QR_RESEND', 'ATTENDANCE_ALERT', 'AUTOMATION') NOT NULL,
    related_user_id BIGINT UNSIGNED NULL,
    related_student_pk BIGINT UNSIGNED NULL,
    subject_line VARCHAR(200) NOT NULL,
    message_preview TEXT NOT NULL,
    delivery_status ENUM('QUEUED', 'SENT', 'FAILED') NOT NULL DEFAULT 'QUEUED',
    provider_message_id VARCHAR(120) NULL,
    error_message TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (email_id),
    KEY idx_email_dispatch_logs_status_time (delivery_status, created_at),
    CONSTRAINT fk_email_dispatch_logs_user
        FOREIGN KEY (related_user_id) REFERENCES users (user_id)
        ON DELETE SET NULL,
    CONSTRAINT fk_email_dispatch_logs_student
        FOREIGN KEY (related_student_pk) REFERENCES student_profiles (student_pk)
        ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS audit_logs (
    audit_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT UNSIGNED NULL,
    action_type VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(80) NOT NULL,
    old_values_json JSON NULL,
    new_values_json JSON NULL,
    notes TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (audit_id),
    KEY idx_audit_logs_entity (entity_type, entity_id, created_at),
    CONSTRAINT fk_audit_logs_actor
        FOREIGN KEY (actor_user_id) REFERENCES users (user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS iot_device_heartbeats (
    heartbeat_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    device_id BIGINT UNSIGNED NOT NULL,
    status_snapshot JSON NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (heartbeat_id),
    KEY idx_iot_device_heartbeats_device_time (device_id, received_at),
    CONSTRAINT fk_iot_device_heartbeats_device
        FOREIGN KEY (device_id) REFERENCES iot_devices (device_id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS automation_rules (
    automation_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    rule_name VARCHAR(150) NOT NULL,
    trigger_type ENUM('DAILY_SUMMARY', 'LOW_ATTENDANCE', 'FAILED_EMAIL', 'DEVICE_OFFLINE', 'MANUAL') NOT NULL,
    schedule_expression VARCHAR(120) NOT NULL,
    config_json JSON NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_by_user_id BIGINT UNSIGNED NOT NULL,
    last_run_at TIMESTAMP NULL DEFAULT NULL,
    next_run_at TIMESTAMP NULL DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (automation_id),
    CONSTRAINT fk_automation_rules_creator
        FOREIGN KEY (created_by_user_id) REFERENCES users (user_id)
        ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS automation_runs (
    run_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    automation_id BIGINT UNSIGNED NOT NULL,
    run_status ENUM('SUCCESS', 'FAILED', 'RUNNING', 'SKIPPED') NOT NULL DEFAULT 'RUNNING',
    output_summary TEXT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (run_id),
    KEY idx_automation_runs_rule_time (automation_id, started_at),
    CONSTRAINT fk_automation_runs_rule
        FOREIGN KEY (automation_id) REFERENCES automation_rules (automation_id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ai_insights (
    insight_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    insight_type ENUM('LOW_ATTENDANCE_RISK', 'ANOMALY', 'TREND', 'RECOMMENDATION') NOT NULL,
    target_type ENUM('SYSTEM', 'TEACHER', 'STUDENT', 'SUBJECT', 'SESSION') NOT NULL,
    target_id VARCHAR(80) NOT NULL,
    score DECIMAL(5,2) NULL,
    summary_text TEXT NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (insight_id),
    KEY idx_ai_insights_target (target_type, target_id, generated_at)
) ENGINE=InnoDB;

CREATE OR REPLACE VIEW v_active_teacher_schedule_today AS
SELECT
    s.schedule_id,
    s.teacher_user_id,
    u.full_name AS teacher_name,
    s.subject_name,
    s.room_name,
    s.day_of_week,
    s.start_time,
    s.end_time,
    s.schedule_status
FROM teacher_schedules s
JOIN users u
    ON u.user_id = s.teacher_user_id
WHERE s.schedule_status = 'APPROVED';

CREATE OR REPLACE VIEW v_daily_attendance_summary AS
SELECT
    DATE(ar.recorded_at) AS attendance_date,
    ar.teacher_user_id,
    u.full_name AS teacher_name,
    ar.subject_name,
    COUNT(*) AS total_records,
    SUM(CASE WHEN ar.attendance_status = 'PRESENT' THEN 1 ELSE 0 END) AS present_count,
    SUM(CASE WHEN ar.attendance_status = 'LATE' THEN 1 ELSE 0 END) AS late_count,
    SUM(CASE WHEN ar.attendance_status = 'ABSENT' THEN 1 ELSE 0 END) AS absent_count
FROM attendance_records ar
JOIN users u
    ON u.user_id = ar.teacher_user_id
GROUP BY DATE(ar.recorded_at), ar.teacher_user_id, u.full_name, ar.subject_name;

INSERT INTO users (user_id, full_name, email, password_hash, role, account_status, must_change_password)
VALUES
    (1, 'Campus Admin', 'admin@qrattend.local', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'ADMIN', 'ACTIVE', 0),
    (2, 'Ava Santos', 'ava.santos@campus.edu', '63cb9c6fa2d65784658539a93ad47f2274a02ddff344537beb97bd399938ad22', 'TEACHER', 'ACTIVE', 1),
    (3, 'Miguel Dela Cruz', 'miguel.cruz@campus.edu', '24e01a260fd3f036925e4438acf3c9d73a726fdf3965a1cc9548c2962b094f40', 'TEACHER', 'ACTIVE', 1)
ON DUPLICATE KEY UPDATE
    full_name = VALUES(full_name),
    password_hash = VALUES(password_hash),
    role = VALUES(role),
    account_status = VALUES(account_status);

INSERT INTO teacher_profiles (teacher_user_id, employee_code, schedule_edit_mode, notes)
VALUES
    (2, 'T-0002', 'APPROVAL_REQUIRED', 'Seed teacher account'),
    (3, 'T-0003', 'APPROVAL_REQUIRED', 'Seed teacher account')
ON DUPLICATE KEY UPDATE
    employee_code = VALUES(employee_code),
    schedule_edit_mode = VALUES(schedule_edit_mode),
    notes = VALUES(notes);

INSERT INTO student_profiles (student_pk, student_code, full_name, email, section_name, qr_status, account_status, created_by_teacher_id, managed_by_admin_id)
VALUES
    (1, '2026-001', 'Lian Reyes', 'lian.reyes@student.edu', 'BSIT-2A', 'SENT', 'ACTIVE', 2, 1),
    (2, '2026-002', 'Mika Flores', 'mika.flores@student.edu', 'BSIT-2A', 'SENT', 'ACTIVE', 2, 1),
    (3, '2026-101', 'Elise Tan', 'elise.tan@student.edu', 'BSCS-3A', 'SENT', 'ACTIVE', 3, 1)
ON DUPLICATE KEY UPDATE
    full_name = VALUES(full_name),
    email = VALUES(email),
    section_name = VALUES(section_name),
    qr_status = VALUES(qr_status),
    account_status = VALUES(account_status),
    created_by_teacher_id = VALUES(created_by_teacher_id),
    managed_by_admin_id = VALUES(managed_by_admin_id);

INSERT INTO teacher_student_assignments (teacher_user_id, student_pk, assignment_status)
VALUES
    (2, 1, 'ACTIVE'),
    (2, 2, 'ACTIVE'),
    (3, 3, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    assignment_status = VALUES(assignment_status);

INSERT INTO student_roster_change_requests
    (removal_request_id, teacher_user_id, student_pk, request_type, request_status, reason, reviewed_by_user_id, reviewed_at)
VALUES
    (1, 2, 2, 'REMOVE_FROM_LIST', 'PENDING', 'Student transferred to another adviser list.', NULL, NULL)
ON DUPLICATE KEY UPDATE
    request_status = VALUES(request_status),
    reason = VALUES(reason),
    reviewed_by_user_id = VALUES(reviewed_by_user_id),
    reviewed_at = VALUES(reviewed_at);

INSERT INTO student_qr_tokens (student_pk, token_hash, token_type, is_active, emailed_at)
VALUES
    (1, SHA2('seed-qr-2026-001', 256), 'PERMANENT', 1, CURRENT_TIMESTAMP),
    (2, SHA2('seed-qr-2026-002', 256), 'PERMANENT', 1, CURRENT_TIMESTAMP),
    (3, SHA2('seed-qr-2026-101', 256), 'PERMANENT', 1, CURRENT_TIMESTAMP)
ON DUPLICATE KEY UPDATE
    is_active = VALUES(is_active),
    emailed_at = VALUES(emailed_at);

INSERT INTO teacher_schedules (schedule_id, teacher_user_id, subject_code, subject_name, day_of_week, start_time, end_time, room_name, schedule_status, created_by_user_id, updated_by_user_id)
VALUES
    (1, 2, 'SE101', 'Software Engineering', 3, '09:00:00', '10:30:00', 'Lab 2', 'APPROVED', 1, 1),
    (2, 2, 'HCI201', 'Human Computer Interaction', 3, '13:00:00', '14:30:00', 'Room 405', 'APPROVED', 1, 1),
    (3, 3, 'NET301', 'Computer Networks', 3, '10:30:00', '12:00:00', 'Lab 1', 'APPROVED', 1, 1)
ON DUPLICATE KEY UPDATE
    subject_code = VALUES(subject_code),
    subject_name = VALUES(subject_name),
    day_of_week = VALUES(day_of_week),
    start_time = VALUES(start_time),
    end_time = VALUES(end_time),
    room_name = VALUES(room_name),
    schedule_status = VALUES(schedule_status),
    updated_by_user_id = VALUES(updated_by_user_id);

INSERT INTO schedule_change_requests (
    request_id,
    schedule_id,
    teacher_user_id,
    old_snapshot,
    requested_subject_name,
    requested_day_of_week,
    requested_start_time,
    requested_end_time,
    requested_room_name,
    requested_snapshot,
    reason,
    request_status,
    reviewed_by_user_id,
    reviewed_at
)
VALUES
    (
        1,
        2,
        2,
        JSON_OBJECT('subject_name', 'Human Computer Interaction', 'day_of_week', 3, 'start_time', '13:00:00', 'end_time', '14:30:00', 'room_name', 'Room 405'),
        'Human Computer Interaction',
        3,
        '13:15:00',
        '14:45:00',
        'Room 406',
        JSON_OBJECT('subject_name', 'Human Computer Interaction', 'day_of_week', 3, 'start_time', '13:15:00', 'end_time', '14:45:00', 'room_name', 'Room 406'),
        'Room 405 is unavailable because of maintenance.',
        'PENDING',
        NULL,
        NULL
    )
ON DUPLICATE KEY UPDATE
    reason = VALUES(reason),
    request_status = VALUES(request_status);

INSERT INTO email_dispatch_logs (
    recipient_email,
    email_type,
    related_user_id,
    related_student_pk,
    subject_line,
    message_preview,
    delivery_status,
    sent_at
)
VALUES
    ('ava.santos@campus.edu', 'TEACHER_PASSWORD', 2, NULL, 'Teacher account created', 'Teacher password email queued.', 'SENT', CURRENT_TIMESTAMP),
    ('miguel.cruz@campus.edu', 'TEACHER_PASSWORD', 3, NULL, 'Teacher account created', 'Teacher password email queued.', 'SENT', CURRENT_TIMESTAMP),
    ('lian.reyes@student.edu', 'STUDENT_QR', NULL, 1, 'Permanent QR code', 'Student QR code email queued.', 'SENT', CURRENT_TIMESTAMP),
    ('mika.flores@student.edu', 'STUDENT_QR', NULL, 2, 'Permanent QR code', 'Student QR code email queued.', 'SENT', CURRENT_TIMESTAMP),
    ('elise.tan@student.edu', 'STUDENT_QR', NULL, 3, 'Permanent QR code', 'Student QR code email queued.', 'SENT', CURRENT_TIMESTAMP);

ALTER TABLE users AUTO_INCREMENT = 1000;
ALTER TABLE student_profiles AUTO_INCREMENT = 1000;
ALTER TABLE teacher_student_assignments AUTO_INCREMENT = 1000;
ALTER TABLE teacher_schedules AUTO_INCREMENT = 1000;
ALTER TABLE schedule_change_requests AUTO_INCREMENT = 1000;
ALTER TABLE student_roster_change_requests AUTO_INCREMENT = 1000;
ALTER TABLE attendance_sessions AUTO_INCREMENT = 1000;
ALTER TABLE student_qr_tokens AUTO_INCREMENT = 1000;
ALTER TABLE iot_devices AUTO_INCREMENT = 1000;
ALTER TABLE qr_scan_logs AUTO_INCREMENT = 1000;
ALTER TABLE attendance_records AUTO_INCREMENT = 1000;
ALTER TABLE email_dispatch_logs AUTO_INCREMENT = 1000;
ALTER TABLE audit_logs AUTO_INCREMENT = 1000;
ALTER TABLE iot_device_heartbeats AUTO_INCREMENT = 1000;
ALTER TABLE automation_rules AUTO_INCREMENT = 1000;
ALTER TABLE automation_runs AUTO_INCREMENT = 1000;
ALTER TABLE ai_insights AUTO_INCREMENT = 1000;
