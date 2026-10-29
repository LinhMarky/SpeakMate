-- Run ONCE only on an existing SpeakMate v2 database. Fresh installs use root speakmate.sql.
-- Back up first and stop the application while applying. Existing accounts/content remain.
-- This upgrade invalidates old refresh tokens; users sign in again.
USE speakmate;
SET SESSION time_zone = '+00:00';

UPDATE users SET refresh_token = NULL;
ALTER TABLE users
    MODIFY COLUMN password VARCHAR(200) NULL COMMENT 'BCrypt hash; NULL for Google-only accounts',
    ADD COLUMN google_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER password,
    ADD COLUMN refresh_expires_at DATETIME(6) NULL AFTER refresh_token,
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0 AFTER refresh_expires_at,
    ADD COLUMN password_reset_code_hash VARCHAR(200) NULL AFTER auth_version,
    ADD COLUMN password_reset_expires_at DATETIME(6) NULL AFTER password_reset_code_hash,
    ADD COLUMN password_reset_sent_at DATETIME(6) NULL AFTER password_reset_expires_at,
    ADD COLUMN password_reset_attempts INT NOT NULL DEFAULT 0 AFTER password_reset_sent_at,
    ADD CONSTRAINT uk_users_google_subject UNIQUE (google_subject),
    DROP CHECK chk_users_password,
    ADD CONSTRAINT chk_users_password CHECK (password IS NULL OR CHAR_LENGTH(TRIM(password)) > 0),
    ADD CONSTRAINT chk_users_identity CHECK (password IS NOT NULL OR google_subject IS NOT NULL),
    ADD CONSTRAINT chk_users_google_subject CHECK (google_subject IS NULL OR
        (CHAR_LENGTH(TRIM(google_subject)) > 0 AND google_subject = TRIM(google_subject))),
    ADD CONSTRAINT chk_users_refresh CHECK (
        (refresh_token IS NULL AND refresh_expires_at IS NULL) OR
        (refresh_token IS NOT NULL AND refresh_expires_at IS NOT NULL AND CHAR_LENGTH(refresh_token) = 64)),
    ADD CONSTRAINT chk_users_auth_version CHECK (auth_version >= 0),
    ADD CONSTRAINT chk_users_reset_attempts CHECK (password_reset_attempts BETWEEN 0 AND 5),
    ADD CONSTRAINT chk_users_reset CHECK (
        (password_reset_code_hash IS NULL AND password_reset_expires_at IS NULL AND password_reset_attempts = 0) OR
        (password IS NOT NULL AND password_reset_code_hash IS NOT NULL
            AND CHAR_LENGTH(TRIM(password_reset_code_hash)) > 0
            AND password_reset_expires_at IS NOT NULL AND password_reset_sent_at IS NOT NULL
            AND password_reset_expires_at > password_reset_sent_at));
