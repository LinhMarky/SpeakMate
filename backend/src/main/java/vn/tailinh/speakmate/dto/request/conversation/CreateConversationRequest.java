package vn.tailinh.speakmate.dto.request.conversation;

import jakarta.validation.constraints.*;
import vn.tailinh.speakmate.util.constant.EnglishLevel;

public record CreateConversationRequest(
    @NotNull
    @Positive
    Long topicId,
    @NotNull
    EnglishLevel level,
    @NotNull
    @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    String createRequestId) {
}
