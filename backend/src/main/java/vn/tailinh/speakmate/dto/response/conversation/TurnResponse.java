package vn.tailinh.speakmate.dto.response.conversation;

public record TurnResponse(
    Long conversationId,
    String requestId,
    String replyStatus,
    MessageResponse userMessage,
    MessageResponse aiMessage,
    int pollAfterSeconds,
    boolean replayed) {
}
