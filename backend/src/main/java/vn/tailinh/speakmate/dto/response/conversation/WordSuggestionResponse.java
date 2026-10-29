package vn.tailinh.speakmate.dto.response.conversation;

public record WordSuggestionResponse(
    Long id,
    String word,
    String meaning,
    String example,
    Long savedWordId) {
}
