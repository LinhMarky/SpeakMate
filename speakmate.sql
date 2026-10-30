-- SpeakMate - Database v3 - 24/10/2026
-- MySQL 8.0.16+ / InnoDB / utf8mb4. Seven tables, including the later word-practice feature.
-- Current specification: Docs/SpeakMate-thiet-ke-co-so-du-lieu.md
-- Fresh install only: use a new/empty database. This is NOT an upgrade migration.
-- No DROP, sample credentials, triggers, stored procedures or automatic role assignment.
-- USER/ADMIN share users. Admin manages topics and accounts through backend authorization.

SET NAMES utf8mb4;
SET SESSION time_zone = '+00:00';
SET SESSION sql_mode = 'STRICT_TRANS_TABLES,ONLY_FULL_GROUP_BY,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

CREATE DATABASE IF NOT EXISTS speakmate
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci;
USE speakmate;

-- 1. Identity and fixed roles. One current refresh token per account.
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(200) NULL COMMENT 'BCrypt hash; NULL for Google-only accounts',
    google_subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    level VARCHAR(2) COLLATE utf8mb4_0900_as_cs NOT NULL DEFAULT 'A1',
    role VARCHAR(20) COLLATE utf8mb4_0900_as_cs NOT NULL DEFAULT 'USER',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    refresh_token TEXT NULL COMMENT 'SHA-256 hash of one current opaque refresh token',
    refresh_expires_at DATETIME(6) NULL,
    auth_version BIGINT NOT NULL DEFAULT 0,
    password_reset_code_hash VARCHAR(200) NULL,
    password_reset_expires_at DATETIME(6) NULL,
    password_reset_sent_at DATETIME(6) NULL,
    password_reset_attempts INT NOT NULL DEFAULT 0,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(255) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_google_subject UNIQUE (google_subject),
    CONSTRAINT chk_users_name CHECK (CHAR_LENGTH(TRIM(name)) > 0 AND name = TRIM(name)),
    CONSTRAINT chk_users_email CHECK (CHAR_LENGTH(TRIM(email)) > 0 AND email = TRIM(email)),
    CONSTRAINT chk_users_password CHECK (password IS NULL OR CHAR_LENGTH(TRIM(password)) > 0),
    CONSTRAINT chk_users_identity CHECK (password IS NOT NULL OR google_subject IS NOT NULL),
    CONSTRAINT chk_users_google_subject CHECK (google_subject IS NULL OR
        (CHAR_LENGTH(TRIM(google_subject)) > 0 AND google_subject = TRIM(google_subject))),
    CONSTRAINT chk_users_refresh CHECK (
        (refresh_token IS NULL AND refresh_expires_at IS NULL) OR
        (refresh_token IS NOT NULL AND refresh_expires_at IS NOT NULL AND CHAR_LENGTH(refresh_token) = 64)),
    CONSTRAINT chk_users_auth_version CHECK (auth_version >= 0),
    CONSTRAINT chk_users_reset_attempts CHECK (password_reset_attempts BETWEEN 0 AND 5),
    CONSTRAINT chk_users_reset CHECK (
        (password_reset_code_hash IS NULL AND password_reset_expires_at IS NULL AND password_reset_attempts = 0) OR
        (password IS NOT NULL AND password_reset_code_hash IS NOT NULL
            AND CHAR_LENGTH(TRIM(password_reset_code_hash)) > 0
            AND password_reset_expires_at IS NOT NULL AND password_reset_sent_at IS NOT NULL
            AND password_reset_expires_at > password_reset_sent_at)),
    CONSTRAINT chk_users_level CHECK (level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_active CHECK (is_active IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- 2. One topic is one playable situation with an AI role/context.
-- Topic.level is suggested difficulty; the learner can choose another valid level.
CREATE TABLE topics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    level VARCHAR(2) COLLATE utf8mb4_0900_as_cs NOT NULL DEFAULT 'A1',
    scenario TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(255) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT pk_topics PRIMARY KEY (id),
    CONSTRAINT uk_topics_name UNIQUE (name),
    CONSTRAINT chk_topics_name CHECK (CHAR_LENGTH(TRIM(name)) > 0 AND name = TRIM(name)),
    CONSTRAINT chk_topics_level CHECK (level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    CONSTRAINT chk_topics_scenario CHECK (CHAR_LENGTH(TRIM(scenario)) > 0),
    CONSTRAINT chk_topics_active CHECK (is_active IN (0, 1)),
    INDEX idx_topics_active_level (is_active, level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- 3. Personal vocabulary and latest self-reported review. No deck required.
CREATE TABLE saved_words (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    word VARCHAR(100) NOT NULL,
    meaning VARCHAR(255) NOT NULL,
    example TEXT NULL,
    status VARCHAR(20) COLLATE utf8mb4_0900_as_cs NOT NULL DEFAULT 'NEW',
    last_reviewed_at DATETIME(6) NULL,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(255) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT pk_saved_words PRIMARY KEY (id),
    CONSTRAINT uk_saved_words_user_word UNIQUE (user_id, word),
    CONSTRAINT fk_saved_words_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_saved_words_word CHECK (CHAR_LENGTH(TRIM(word)) > 0 AND word = TRIM(word)),
    CONSTRAINT chk_saved_words_meaning CHECK (CHAR_LENGTH(TRIM(meaning)) > 0),
    CONSTRAINT chk_saved_words_status CHECK (status IN ('NEW', 'LEARNING', 'MASTERED')),
    CONSTRAINT chk_saved_words_review CHECK (
        (status = 'NEW' AND last_reviewed_at IS NULL)
        OR (status IN ('LEARNING', 'MASTERED') AND last_reviewed_at IS NOT NULL)
    ),
    INDEX idx_saved_words_review (user_id, status, last_reviewed_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- 4. Closing the conversation does not depend on a successful review.
-- NOT_STARTED may hold a reply token. PROCESSING must hold a review token.
CREATE TABLE conversations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    create_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    profile_level_snapshot VARCHAR(2) COLLATE utf8mb4_0900_as_cs NOT NULL,
    level VARCHAR(2) COLLATE utf8mb4_0900_as_cs NOT NULL,
    topic_name_snapshot VARCHAR(100) NOT NULL,
    scenario_snapshot TEXT NOT NULL,
    ended_at DATETIME(6) NULL,
    review_status VARCHAR(20) COLLATE utf8mb4_0900_as_cs NOT NULL DEFAULT 'NOT_STARTED',
    summary TEXT NULL,
    strengths TEXT NULL,
    weaknesses TEXT NULL,
    recommended_level VARCHAR(2) COLLATE utf8mb4_0900_as_cs NULL,
    level_feedback TEXT NULL,
    self_rating TINYINT NULL,
    processing_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'New backend UUID per AI attempt; compare before every result/failure write',
    processing_started_at DATETIME(6) NULL,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(255) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT pk_conversations PRIMARY KEY (id),
    CONSTRAINT uk_conversations_create_request UNIQUE (user_id, create_request_id),
    CONSTRAINT fk_conversations_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_conversations_topic FOREIGN KEY (topic_id) REFERENCES topics (id),
    CONSTRAINT chk_conversations_create_request CHECK (CHAR_LENGTH(create_request_id) = 36),
    CONSTRAINT chk_conversations_profile_level CHECK (profile_level_snapshot IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    CONSTRAINT chk_conversations_level CHECK (level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    CONSTRAINT chk_conversations_topic_name CHECK (CHAR_LENGTH(TRIM(topic_name_snapshot)) > 0),
    CONSTRAINT chk_conversations_scenario CHECK (CHAR_LENGTH(TRIM(scenario_snapshot)) > 0),
    CONSTRAINT chk_conversations_review_status CHECK (
        review_status IN ('NOT_STARTED', 'PROCESSING', 'COMPLETED', 'FAILED', 'INSUFFICIENT_DATA')
    ),
    CONSTRAINT chk_conversations_closed CHECK (
        (review_status = 'NOT_STARTED' AND ended_at IS NULL)
        OR (review_status <> 'NOT_STARTED' AND ended_at IS NOT NULL)
    ),
    CONSTRAINT chk_conversations_processing_pair CHECK (
        (processing_token IS NULL AND processing_started_at IS NULL)
        OR (processing_token IS NOT NULL AND CHAR_LENGTH(processing_token) = 36
            AND processing_started_at IS NOT NULL)
    ),
    CONSTRAINT chk_conversations_processing_state CHECK (
        review_status = 'NOT_STARTED'
        OR (review_status = 'PROCESSING' AND processing_token IS NOT NULL)
        OR (review_status IN ('COMPLETED', 'FAILED', 'INSUFFICIENT_DATA') AND processing_token IS NULL)
    ),
    CONSTRAINT chk_conversations_report CHECK (
        (review_status IN ('NOT_STARTED', 'PROCESSING', 'FAILED')
            AND summary IS NULL AND strengths IS NULL AND weaknesses IS NULL
            AND recommended_level IS NULL AND level_feedback IS NULL)
        OR (review_status = 'COMPLETED'
            AND summary IS NOT NULL AND strengths IS NOT NULL AND weaknesses IS NOT NULL
            AND recommended_level IS NOT NULL AND level_feedback IS NOT NULL)
        OR (review_status = 'INSUFFICIENT_DATA'
            AND summary IS NOT NULL AND strengths IS NULL AND weaknesses IS NULL
            AND recommended_level IS NULL AND level_feedback IS NULL)
    ),
    CONSTRAINT chk_conversations_summary CHECK (summary IS NULL OR CHAR_LENGTH(TRIM(summary)) > 0),
    CONSTRAINT chk_conversations_strengths CHECK (strengths IS NULL OR CHAR_LENGTH(TRIM(strengths)) > 0),
    CONSTRAINT chk_conversations_weaknesses CHECK (weaknesses IS NULL OR CHAR_LENGTH(TRIM(weaknesses)) > 0),
    CONSTRAINT chk_conversations_recommendation CHECK (
        recommended_level IS NULL OR recommended_level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')
    ),
    CONSTRAINT chk_conversations_level_feedback CHECK (
        level_feedback IS NULL OR CHAR_LENGTH(TRIM(level_feedback)) > 0
    ),
    CONSTRAINT chk_conversations_rating CHECK (
        self_rating IS NULL OR (self_rating BETWEEN 1 AND 5 AND ended_at IS NOT NULL)
    ),
    CONSTRAINT chk_conversations_ended_time CHECK (ended_at IS NULL OR ended_at >= created_at),
    INDEX idx_conversations_user_created (user_id, created_at, id),
    INDEX idx_conversations_topic_created (topic_id, created_at),
    INDEX idx_conversations_review_ended (review_status, ended_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- 5. Original transcript. Corrections are written ONLY during the final review.
-- Service serializes turns under the conversation lock; read by id ASC.
CREATE TABLE messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    sender VARCHAR(10) COLLATE utf8mb4_0900_as_cs NOT NULL,
    content TEXT NOT NULL,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'Client UUID; a USER message and its AI reply share this value',
    corrected_content TEXT NULL,
    feedback TEXT NULL,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(255) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT pk_messages PRIMARY KEY (id),
    CONSTRAINT uk_messages_request_sender UNIQUE (conversation_id, request_id, sender),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT chk_messages_sender CHECK (sender IN ('USER', 'AI')),
    CONSTRAINT chk_messages_content CHECK (
        CHAR_LENGTH(TRIM(content)) > 0
        AND ((sender = 'USER' AND CHAR_LENGTH(content) <= 1000)
            OR (sender = 'AI' AND CHAR_LENGTH(content) <= 4000))
    ),
    CONSTRAINT chk_messages_request CHECK (CHAR_LENGTH(request_id) = 36),
    CONSTRAINT chk_messages_correction CHECK (
        (corrected_content IS NULL AND feedback IS NULL)
        OR (sender = 'USER' AND corrected_content IS NOT NULL AND feedback IS NOT NULL
            AND CHAR_LENGTH(TRIM(corrected_content)) BETWEEN 1 AND 4000
            AND CHAR_LENGTH(TRIM(feedback)) > 0)
    ),
    INDEX idx_messages_conversation_id (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- 6. Historical suggestions, even when the learner saves none.
-- Query saved_words for current membership instead of storing an is_saved flag.
CREATE TABLE word_suggestions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    word VARCHAR(100) NOT NULL,
    meaning VARCHAR(255) NOT NULL,
    example TEXT NULL,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(255) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT pk_word_suggestions PRIMARY KEY (id),
    CONSTRAINT uk_word_suggestions_conversation_word UNIQUE (conversation_id, word),
    CONSTRAINT fk_word_suggestions_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT chk_word_suggestions_word CHECK (CHAR_LENGTH(TRIM(word)) > 0 AND word = TRIM(word)),
    CONSTRAINT chk_word_suggestions_meaning CHECK (CHAR_LENGTH(TRIM(meaning)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- 7. Later feature: one selected personal word practiced in one conversation.
-- A normal chat has zero rows here. Deleting a saved word preserves its historical snapshot.
CREATE TABLE word_practices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    saved_word_id BIGINT NULL,
    word_snapshot VARCHAR(100) NOT NULL,
    meaning_snapshot VARCHAR(255) NOT NULL,
    result VARCHAR(20) COLLATE utf8mb4_0900_as_cs NOT NULL DEFAULT 'PENDING',
    evidence_message_id BIGINT NULL,
    feedback TEXT NULL,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(255) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT pk_word_practices PRIMARY KEY (id),
    CONSTRAINT uk_word_practices_conversation_word UNIQUE (conversation_id, word_snapshot),
    CONSTRAINT uk_word_practices_conversation_saved UNIQUE (conversation_id, saved_word_id),
    CONSTRAINT fk_word_practices_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_word_practices_saved_word FOREIGN KEY (saved_word_id) REFERENCES saved_words (id) ON DELETE SET NULL,
    CONSTRAINT fk_word_practices_evidence FOREIGN KEY (evidence_message_id) REFERENCES messages (id),
    CONSTRAINT chk_word_practices_word CHECK (
        CHAR_LENGTH(TRIM(word_snapshot)) > 0 AND word_snapshot = TRIM(word_snapshot)
    ),
    CONSTRAINT chk_word_practices_meaning CHECK (CHAR_LENGTH(TRIM(meaning_snapshot)) > 0),
    CONSTRAINT chk_word_practices_result CHECK (result IN ('PENDING', 'USED', 'NOT_USED', 'UNVERIFIED')),
    CONSTRAINT chk_word_practices_evidence CHECK (
        (result = 'USED' AND evidence_message_id IS NOT NULL)
        OR (result <> 'USED' AND evidence_message_id IS NULL)
    ),
    CONSTRAINT chk_word_practices_feedback CHECK (
        (result = 'PENDING' AND feedback IS NULL)
        OR (result <> 'PENDING' AND feedback IS NOT NULL AND CHAR_LENGTH(TRIM(feedback)) > 0)
    ),
    INDEX idx_word_practices_saved_word (saved_word_id),
    INDEX idx_word_practices_evidence (evidence_message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

-- Other FKs use RESTRICT. Disable used topics/accounts instead of deleting history.
-- Service: roles/ownership/active accounts, active topics at creation, same-conversation USER
-- evidence, max 3 selected words, UUID format, immutable snapshots, end-only review writes,
-- state transitions, and request/token retry rules. CHECK validates rows, not whole use-cases.
-- Set every application connection to UTC; SET SESSION above affects only this import.
-- JPA auditing updates audit columns; there is no SQL update trigger.

-- Chọn đúng database trước khi chạy. Chỉ thêm 2 bảng, không đổi/xóa dữ liệu cũ.
CREATE TABLE IF NOT EXISTS system_decks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    level VARCHAR(2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_at DATETIME(6) NULL,
    updated_by VARCHAR(255) NULL,
    CONSTRAINT chk_system_decks_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_system_decks_level CHECK (level IN ('A1','A2','B1','B2','C1','C2'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;

CREATE TABLE IF NOT EXISTS system_deck_words (
    deck_id BIGINT NOT NULL,
    position INT NOT NULL,
    word VARCHAR(100) NOT NULL,
    meaning VARCHAR(255) NOT NULL,
    example TEXT NULL,
    PRIMARY KEY (deck_id, position),
    CONSTRAINT fk_system_deck_words_deck FOREIGN KEY (deck_id) REFERENCES system_decks(id) ON DELETE CASCADE,
    CONSTRAINT chk_system_deck_word CHECK (CHAR_LENGTH(TRIM(word)) > 0),
    CONSTRAINT chk_system_deck_meaning CHECK (CHAR_LENGTH(TRIM(meaning)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_ci;
