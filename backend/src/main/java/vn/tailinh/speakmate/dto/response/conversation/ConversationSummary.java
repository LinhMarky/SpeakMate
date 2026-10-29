package vn.tailinh.speakmate.dto.response.conversation;

import java.time.Instant;
import vn.tailinh.speakmate.util.constant.*;

public record ConversationSummary(
    Long id,
    Long topicId,
    String topicName,
    EnglishLevel profileLevelSnapshot,
    EnglishLevel level,
    String status,
    ReviewStatus reviewStatus,
    Instant createdAt,
    Instant endedAt) {
}
