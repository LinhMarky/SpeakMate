package vn.tailinh.speakmate.dto.response.conversation;

import java.util.List;
import vn.tailinh.speakmate.util.constant.*;

public record ReviewResponse(
    Long conversationId,
    ReviewStatus reviewStatus,
    String summary,
    String strengths,
    String weaknesses,
    EnglishLevel recommendedLevel,
    String levelFeedback,
    List<GrammarCorrection> grammarCorrections,
    List<WordSuggestionResponse> suggestedWords,
    boolean canRetry,
    int pollAfterSeconds) {
}
