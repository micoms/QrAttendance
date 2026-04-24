USE qrattend_db;

ALTER TABLE student_qr_tokens
    ADD COLUMN IF NOT EXISTS token_hash CHAR(64) NULL AFTER student_pk;

UPDATE student_qr_tokens
SET token_hash = COALESCE(token_hash, SHA2(qr_token, 256))
WHERE token_hash IS NULL
  AND qr_token IS NOT NULL;

UPDATE student_qr_tokens
SET qr_token = NULL
WHERE qr_token IS NOT NULL;

ALTER TABLE student_qr_tokens
    MODIFY COLUMN token_hash CHAR(64) NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_student_qr_tokens_hash
    ON student_qr_tokens (token_hash);

ALTER TABLE qr_scan_logs
    ADD COLUMN IF NOT EXISTS token_hash CHAR(64) NULL AFTER device_id,
    ADD COLUMN IF NOT EXISTS payload_preview VARCHAR(180) NOT NULL DEFAULT 'QR scan received.' AFTER token_hash;

UPDATE qr_scan_logs
SET token_hash = COALESCE(token_hash, CASE
        WHEN qr_token IS NULL OR qr_token = '' THEN NULL
        ELSE SHA2(qr_token, 256)
    END),
    payload_preview = 'QR scan received.',
    raw_payload = 'QR scan received.',
    qr_token = NULL;

UPDATE email_dispatch_logs
SET message_preview = 'Teacher password email queued.'
WHERE email_type IN ('TEACHER_PASSWORD', 'PASSWORD_RESET');

UPDATE email_dispatch_logs
SET message_preview = 'Student QR code email queued.'
WHERE email_type IN ('STUDENT_QR', 'QR_RESEND');
