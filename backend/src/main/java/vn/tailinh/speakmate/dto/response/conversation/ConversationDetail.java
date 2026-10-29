package vn.tailinh.speakmate.dto.response.conversation;

import java.time.Instant;
import java.util.List;
import vn.tailinh.speakmate.util.constant.*;

public record ConversationDetail(
    Long id,
    Long topicId,
    String topicName,
    String scenario,
    EnglishLevel profileLevelSnapshot,
    EnglishLevel level,
    String status,
    ReviewStatus reviewStatus,
    Instant createdAt,
    Instant endedAt,
    int userTurnCount,
    PendingReply pendingReply,
    List<MessageResponse> messages) {
}
