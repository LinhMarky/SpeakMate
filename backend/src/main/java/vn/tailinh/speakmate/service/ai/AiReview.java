package vn.tailinh.speakmate.service.ai;

import java.util.List;
import vn.tailinh.speakmate.util.constant.EnglishLevel;

// AI không tự cấp ID từ gợi ý hoặc câu gốc; backend lấy câu gốc từ tin nhắn đã lưu.
public record AiReview(
    Boolean insufficientData,
    String summary,
    String strengths,
    String weaknesses,
    EnglishLevel recommendedLevel,
    String levelFeedback,
    List<Correction> grammarCorrections,
    List<Word> suggestedWords) {

    public record Correction(
        Long messageId,
        String correctedContent,
        String feedback) {
    }

    public record Word(
        String word,
        String meaning,
        String example) {
    }
}
