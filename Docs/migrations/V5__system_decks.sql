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
