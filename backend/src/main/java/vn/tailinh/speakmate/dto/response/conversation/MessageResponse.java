package vn.tailinh.speakmate.dto.response.conversation;

import java.time.Instant;
import vn.tailinh.speakmate.util.constant.MessageSender;

public record MessageResponse(
    Long id,
    MessageSender sender,
    String content,
    String requestId,
    Instant createdAt) {
}
