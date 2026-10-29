package vn.tailinh.speakmate.dto.response.conversation;

public record PendingReply(
    String requestId,
    Long userMessageId,
    String status,
    int pollAfterSeconds) {
}
