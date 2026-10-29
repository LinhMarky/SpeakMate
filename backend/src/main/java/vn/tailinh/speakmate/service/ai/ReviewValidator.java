package vn.tailinh.speakmate.service.ai;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import vn.tailinh.speakmate.util.constant.MessageSender;

@Component
public class ReviewValidator {

    private static final Pattern ENGLISH_WORD = Pattern.compile("[A-Za-z]+(?:'[A-Za-z]+)?");

    public boolean enoughData(AiConversation input) {
        // Chỉ đếm lời người học: tối thiểu 3 lượt và 30 từ trước khi xin nhận xét.
        var userMessages = input.messages().stream().filter(m -> m.sender() == MessageSender.USER).toList();
        long words = userMessages.stream().mapToLong(m -> ENGLISH_WORD.matcher(m.content()).results().count()).sum();
        return userMessages.size() >= 3 && words >= 30;
    }

    public void validate(AiReview report, AiConversation input) {
        if (report == null || report.insufficientData() == null || !text(report.summary(), 3000)
                || report.grammarCorrections() == null || report.suggestedWords() == null) {
            throw AiFailure.invalid();
        }
        if (report.insufficientData()) {
            if (report.strengths() != null || report.weaknesses() != null || report.recommendedLevel() != null
                    || report.levelFeedback() != null || !report.grammarCorrections().isEmpty()
                    || !report.suggestedWords().isEmpty()) {
                throw AiFailure.invalid();
            }
            return;
        }
        if (!text(report.strengths(), 3000) || !text(report.weaknesses(), 3000) || report.recommendedLevel() == null
                || !text(report.levelFeedback(), 3000) || report.grammarCorrections().size() > 20
                || report.suggestedWords().size() > 10) {
            throw AiFailure.invalid();
        }
        // AI chỉ được sửa tin nhắn USER có trong bài, mỗi tin nhắn tối đa một lần.
        Set<Long> userIds = new HashSet<>();
        input.messages().stream().filter(m -> m.sender() == MessageSender.USER).forEach(m -> userIds.add(m.id()));
        Set<Long> corrected = new HashSet<>();
        for (var correction : report.grammarCorrections()) {
            if (correction == null || !userIds.contains(correction.messageId())
                    || !corrected.add(correction.messageId()) || !text(correction.correctedContent(), 4000)
                    || !text(correction.feedback(), 2000)) {
                throw AiFailure.invalid();
            }
        }
        Set<String> words = new HashSet<>();
        for (var word : report.suggestedWords()) {
            if (word == null || !text(word.word(), 100) || !word.word().equals(word.word().trim())
                    || !words.add(word.word().toLowerCase(Locale.ROOT)) || !text(word.meaning(), 255)
                    || (word.example() != null && word.example().length() > 1000)) {
                throw AiFailure.invalid();
            }
        }
    }

    private boolean text(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max;
    }
}
