-- MySQL 8: run against the explicitly selected existing v3 database.
-- Expands CHECK constraints only; no row updates, deletes or database selection.
-- Fresh installations already include these levels in speakmate.sql.
ALTER TABLE users
    DROP CHECK chk_users_level,
    ADD CONSTRAINT chk_users_level CHECK (level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2'));

ALTER TABLE topics
    DROP CHECK chk_topics_level,
    ADD CONSTRAINT chk_topics_level CHECK (level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2'));

ALTER TABLE conversations
    DROP CHECK chk_conversations_profile_level,
    DROP CHECK chk_conversations_level,
    DROP CHECK chk_conversations_recommendation,
    ADD CONSTRAINT chk_conversations_profile_level
        CHECK (profile_level_snapshot IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    ADD CONSTRAINT chk_conversations_level CHECK (level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    ADD CONSTRAINT chk_conversations_recommendation
        CHECK (recommended_level IS NULL OR recommended_level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2'));
