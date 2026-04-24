USE qrattend_db;

ALTER TABLE student_profiles
    ADD COLUMN IF NOT EXISTS section_name VARCHAR(80) NOT NULL DEFAULT 'Unassigned' AFTER email,
    ADD COLUMN IF NOT EXISTS managed_by_admin_id BIGINT UNSIGNED NULL AFTER created_by_teacher_id;

ALTER TABLE student_profiles
    ADD INDEX IF NOT EXISTS idx_student_profiles_section (section_name);

ALTER TABLE student_profiles
    ADD CONSTRAINT fk_student_profiles_admin
        FOREIGN KEY (managed_by_admin_id) REFERENCES users (user_id)
        ON DELETE SET NULL;

UPDATE student_profiles
SET section_name = CASE student_code
    WHEN '2026-001' THEN 'BSIT-2A'
    WHEN '2026-002' THEN 'BSIT-2A'
    WHEN '2026-003' THEN 'BSIT-2B'
    WHEN '2026-004' THEN 'BSIT-2B'
    WHEN '2026-101' THEN 'BSCS-3A'
    WHEN '2026-102' THEN 'BSCS-3A'
    ELSE COALESCE(NULLIF(section_name, ''), 'Unassigned')
END,
managed_by_admin_id = COALESCE(managed_by_admin_id, 1);

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
