package vn.tailinh.speakmate.dto.response.conversation;

public record GrammarCorrection(
    Long messageId,
    String originalContent,
    String correctedContent,
    String feedback) {
}
