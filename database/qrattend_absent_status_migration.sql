-- Migration: Add ABSENT to attendance_status enum
-- This is a backward-compatible change; existing rows keep their values.
ALTER TABLE attendance_records
    MODIFY attendance_status ENUM('PRESENT', 'LATE', 'ABSENT') NOT NULL DEFAULT 'PRESENT';
