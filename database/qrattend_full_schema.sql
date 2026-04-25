CREATE DATABASE IF NOT EXISTS qrattend_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE qrattend_db;

DROP TABLE IF EXISTS email_logs;
DROP TABLE IF EXISTS attendance_records;
DROP TABLE IF EXISTS attendance_sessions;
DROP TABLE IF EXISTS student_removal_requests;
DROP TABLE IF EXISTS schedule_requests;
DROP TABLE IF EXISTS schedules;
DROP TABLE IF EXISTS students;
DROP TABLE IF EXISTS rooms;
DROP TABLE IF EXISTS subjects;
DROP TABLE IF EXISTS sections;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    user_id INT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    password_hash CHAR(64) NOT NULL,
    role ENUM('ADMIN', 'TEACHER') NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    must_change_password TINYINT(1) NOT NULL DEFAULT 0,
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB;

CREATE TABLE sections (
    section_id INT NOT NULL AUTO_INCREMENT,
    section_name VARCHAR(80) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (section_id),
    UNIQUE KEY uk_sections_name (section_name)
) ENGINE=InnoDB;

CREATE TABLE subjects (
    subject_id INT NOT NULL AUTO_INCREMENT,
    subject_name VARCHAR(120) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (subject_id),
    UNIQUE KEY uk_subjects_name (subject_name)
) ENGINE=InnoDB;

CREATE TABLE rooms (
    room_id INT NOT NULL AUTO_INCREMENT,
    room_name VARCHAR(80) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (room_id),
    UNIQUE KEY uk_rooms_name (room_name)
) ENGINE=InnoDB;

CREATE TABLE students (
    student_id INT NOT NULL AUTO_INCREMENT,
    section_id INT NOT NULL,
    student_code VARCHAR(50) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    qr_hash CHAR(64) NOT NULL,
    qr_status ENUM('NOT_SENT', 'SENT', 'FAILED') NOT NULL DEFAULT 'NOT_SENT',
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id),
    UNIQUE KEY uk_students_code (student_code),
    UNIQUE KEY uk_students_email (email),
    KEY idx_students_section (section_id),
    CONSTRAINT fk_students_section
        FOREIGN KEY (section_id) REFERENCES sections (section_id)
        ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE schedules (
    schedule_id INT NOT NULL AUTO_INCREMENT,
    teacher_user_id INT NOT NULL,
    section_id INT NOT NULL,
    subject_id INT NOT NULL,
    room_id INT NOT NULL,
    day_of_week TINYINT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (schedule_id),
    KEY idx_schedules_teacher_day (teacher_user_id, day_of_week, start_time),
    KEY idx_schedules_section (section_id),
    CONSTRAINT fk_schedules_teacher
        FOREIGN KEY (teacher_user_id) REFERENCES users (user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_schedules_section
        FOREIGN KEY (section_id) REFERENCES sections (section_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_schedules_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (subject_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_schedules_room
        FOREIGN KEY (room_id) REFERENCES rooms (room_id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_schedules_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_schedules_time CHECK (start_time < end_time)
) ENGINE=InnoDB;

CREATE TABLE schedule_requests (
    request_id INT NOT NULL AUTO_INCREMENT,
    schedule_id INT NOT NULL,
    teacher_user_id INT NOT NULL,
    section_id INT NOT NULL,
    subject_id INT NOT NULL,
    room_id INT NOT NULL,
    day_of_week TINYINT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    reason TEXT NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    reviewed_by_user_id INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at DATETIME NULL,
    PRIMARY KEY (request_id),
    KEY idx_schedule_requests_teacher (teacher_user_id, status),
    CONSTRAINT fk_schedule_requests_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedules (schedule_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_schedule_requests_teacher
        FOREIGN KEY (teacher_user_id) REFERENCES users (user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_schedule_requests_section
        FOREIGN KEY (section_id) REFERENCES sections (section_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_schedule_requests_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (subject_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_schedule_requests_room
        FOREIGN KEY (room_id) REFERENCES rooms (room_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_schedule_requests_reviewer
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users (user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE student_removal_requests (
    request_id INT NOT NULL AUTO_INCREMENT,
    teacher_user_id INT NOT NULL,
    student_id INT NOT NULL,
    reason TEXT NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    reviewed_by_user_id INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at DATETIME NULL,
    PRIMARY KEY (request_id),
    KEY idx_student_removal_requests_teacher (teacher_user_id, status),
    CONSTRAINT fk_student_removal_requests_teacher
        FOREIGN KEY (teacher_user_id) REFERENCES users (user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_student_removal_requests_student
        FOREIGN KEY (student_id) REFERENCES students (student_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_student_removal_requests_reviewer
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users (user_id)
        ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE attendance_sessions (
    session_id INT NOT NULL AUTO_INCREMENT,
    teacher_user_id INT NOT NULL,
    schedule_id INT NULL,
    section_id INT NOT NULL,
    subject_id INT NOT NULL,
    room_id INT NULL,
    session_date DATE NOT NULL,
    is_temporary TINYINT(1) NOT NULL DEFAULT 0,
    reason VARCHAR(255) NULL,
    status ENUM('OPEN', 'CLOSED') NOT NULL DEFAULT 'OPEN',
    opened_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at DATETIME NULL,
    PRIMARY KEY (session_id),
    KEY idx_attendance_sessions_teacher (teacher_user_id, session_date, status),
    CONSTRAINT fk_attendance_sessions_teacher
        FOREIGN KEY (teacher_user_id) REFERENCES users (user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_attendance_sessions_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedules (schedule_id)
        ON DELETE SET NULL,
    CONSTRAINT fk_attendance_sessions_section
        FOREIGN KEY (section_id) REFERENCES sections (section_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_sessions_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (subject_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_sessions_room
        FOREIGN KEY (room_id) REFERENCES rooms (room_id)
        ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE attendance_records (
    record_id INT NOT NULL AUTO_INCREMENT,
    session_id INT NOT NULL,
    student_id INT NOT NULL,
    attendance_method ENUM('QR', 'MANUAL') NOT NULL,
    attendance_status ENUM('PRESENT', 'LATE') NOT NULL DEFAULT 'PRESENT',
    note VARCHAR(255) NOT NULL DEFAULT '',
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (record_id),
    UNIQUE KEY uk_attendance_once (session_id, student_id),
    KEY idx_attendance_student (student_id),
    CONSTRAINT fk_attendance_records_session
        FOREIGN KEY (session_id) REFERENCES attendance_sessions (session_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_attendance_records_student
        FOREIGN KEY (student_id) REFERENCES students (student_id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE email_logs (
    email_id INT NOT NULL AUTO_INCREMENT,
    email_type ENUM('TEACHER_PASSWORD', 'STUDENT_QR') NOT NULL,
    related_user_id INT NULL,
    related_student_id INT NULL,
    recipient_email VARCHAR(150) NOT NULL,
    subject_line VARCHAR(200) NOT NULL,
    info_text VARCHAR(255) NOT NULL,
    status ENUM('QUEUED', 'SENT', 'FAILED') NOT NULL DEFAULT 'QUEUED',
    provider_message_id VARCHAR(120) NULL,
    error_text VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at DATETIME NULL,
    PRIMARY KEY (email_id),
    KEY idx_email_logs_status (status, created_at),
    CONSTRAINT fk_email_logs_user
        FOREIGN KEY (related_user_id) REFERENCES users (user_id)
        ON DELETE SET NULL,
    CONSTRAINT fk_email_logs_student
        FOREIGN KEY (related_student_id) REFERENCES students (student_id)
        ON DELETE SET NULL
) ENGINE=InnoDB;

INSERT INTO users (user_id, full_name, email, password_hash, role, is_active, must_change_password)
VALUES
    (1, 'Campus Admin', 'admin@qrattend.local', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'ADMIN', 1, 0)
ON DUPLICATE KEY UPDATE
    full_name = VALUES(full_name),
    password_hash = VALUES(password_hash),
    role = VALUES(role),
    is_active = VALUES(is_active),
    must_change_password = VALUES(must_change_password);

ALTER TABLE users AUTO_INCREMENT = 100;
ALTER TABLE sections AUTO_INCREMENT = 100;
ALTER TABLE subjects AUTO_INCREMENT = 100;
ALTER TABLE rooms AUTO_INCREMENT = 100;
ALTER TABLE students AUTO_INCREMENT = 100;
ALTER TABLE schedules AUTO_INCREMENT = 100;
ALTER TABLE schedule_requests AUTO_INCREMENT = 100;
ALTER TABLE student_removal_requests AUTO_INCREMENT = 100;
ALTER TABLE attendance_sessions AUTO_INCREMENT = 100;
ALTER TABLE attendance_records AUTO_INCREMENT = 100;
ALTER TABLE email_logs AUTO_INCREMENT = 100;
