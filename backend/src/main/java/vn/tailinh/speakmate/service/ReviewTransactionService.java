package vn.tailinh.speakmate.service;

import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tailinh.speakmate.dto.response.conversation.*;
import vn.tailinh.speakmate.entity.*;
import vn.tailinh.speakmate.exception.BusinessException;
import vn.tailinh.speakmate.repository.*;
import vn.tailinh.speakmate.service.ai.*;
import vn.tailinh.speakmate.util.constant.*;
import vn.tailinh.speakmate.util.mapper.ConversationMapper;

@Service
@RequiredArgsConstructor
public class ReviewTransactionService {

    private final ConversationRepository conversations;

    private final MessageRepository messages;

    private final WordSuggestionRepository suggestions;

    private final SavedWordRepository savedWords;

    private final UserService users;

    private final ReviewValidator validator;

    private final Clock clock;

    public record ReviewAttempt(
        Long conversationId,
        String token,
        UserService.CurrentIdentity identity,
        AiConversation input,
        ReviewResponse existing) {
    }

    @Transactional(readOnly = true)
    public ReviewResponse get(long id) {
        Conversation c = conversations.findByIdAndUserId(id, users.getCurrentUser().getId())
                .orElseThrow(BusinessException::notFound);
        return response(c);
    }

    @Transactional
    public ReviewAttempt claim(long id, boolean retry) {
        var identity = users.getCurrentIdentity();
        users.lockIdentity(identity);
        Conversation c = conversations.findOwnedForUpdate(id, identity.userId())
                .orElseThrow(BusinessException::notFound);
        if (retry && c.isOpen()) {
            throw BusinessException.conflict("CONVERSATION_OPEN", "Hãy kết thúc bài trước khi xin nhận xét");
        }
        if (!c.isOpen()) {
            boolean retryable = c.getReviewStatus() == ReviewStatus.FAILED
                    || (c.getReviewStatus() == ReviewStatus.PROCESSING
                            && !ConversationMapper.processingActive(c, clock.instant()));
            if (!retry || !retryable) {
                return new ReviewAttempt(id, null, identity, null, response(c));
            }
        }
        // Đọc hội thoại trước khi đổi trạng thái vì truy vấn có thể khiến JPA tự flush.
        AiConversation input = input(c);
        if (c.isOpen()) {
            c.setEndedAt(clock.instant());
            c.clearProcessing(); // Chặn lời AI đang chờ ghi vào bài đã kết thúc.
        }
        if (!validator.enoughData(input)) {
            c.setReviewStatus(ReviewStatus.INSUFFICIENT_DATA);
            c.setSummary("Bài luyện cần ít nhất 3 lượt nói và 30 từ tiếng Anh để có đủ dữ liệu nhận xét.");
            c.clearProcessing();
            return new ReviewAttempt(id, null, identity, null, response(c));
        }
        c.setReviewStatus(ReviewStatus.PROCESSING);
        c.setProcessingToken(UUID.randomUUID().toString());
        c.setProcessingStartedAt(clock.instant());
        return new ReviewAttempt(id, c.getProcessingToken(), identity, input, null);
    }

    @Transactional
    public ReviewResponse complete(ReviewAttempt attempt, AiReview report) {
        users.lockIdentity(attempt.identity());
        Conversation c = conversations.findOwnedForUpdate(attempt.conversationId(), attempt.identity().userId())
                .orElseThrow(BusinessException::notFound);
        if (!matches(c, attempt)) {
            return response(c);
        }
        // Kiểm tra toàn bộ báo cáo trước, rồi lưu nhận xét, lỗi và từ gợi ý cùng transaction.
        validator.validate(report, attempt.input());
        List<Message> transcript = messages.findByConversationIdOrderByIdAsc(c.getId());
        c.setSummary(report.summary());
        if (report.insufficientData()) {
            c.setReviewStatus(ReviewStatus.INSUFFICIENT_DATA);
        } else {
            c.setStrengths(report.strengths());
            c.setWeaknesses(report.weaknesses());
            c.setRecommendedLevel(report.recommendedLevel());
            c.setLevelFeedback(report.levelFeedback());
            c.setReviewStatus(ReviewStatus.COMPLETED);
            c.clearProcessing();
            for (var correction : report.grammarCorrections()) {
                Message message = transcript.stream().filter(m -> m.getId().equals(correction.messageId())).findFirst()
                        .orElseThrow();
                message.setCorrectedContent(correction.correctedContent());
                message.setFeedback(correction.feedback());
            }
            for (var word : report.suggestedWords()) {
                WordSuggestion suggestion = new WordSuggestion();
                suggestion.setConversation(c);
                suggestion.setWord(word.word());
                suggestion.setMeaning(word.meaning());
                suggestion.setExample(word.example());
                suggestions.save(suggestion);
            }
        }
        c.clearProcessing();
        conversations.flush();
        return response(c);
    }

    @Transactional
    public void fail(ReviewAttempt attempt) {
        users.lockIdentity(attempt.identity());
        Conversation c = conversations.findOwnedForUpdate(attempt.conversationId(), attempt.identity().userId())
                .orElseThrow(BusinessException::notFound);
        if (matches(c, attempt)) {
            c.setReviewStatus(ReviewStatus.FAILED);
            c.clearProcessing();
        }
    }

    private boolean matches(Conversation c, ReviewAttempt attempt) {
        return c.getReviewStatus() == ReviewStatus.PROCESSING && attempt.token().equals(c.getProcessingToken());
    }

    private AiConversation input(Conversation c) {
        return new AiConversation(c.getTopicNameSnapshot(), c.getScenarioSnapshot(), c.getProfileLevelSnapshot(),
                c.getLevel(), messages.findByConversationIdOrderByIdAsc(c.getId()).stream()
                        .map(ConversationMapper::message).toList());
    }

    private ReviewResponse response(Conversation c) {
        List<GrammarCorrection> corrections = List.of();
        List<WordSuggestionResponse> words = List.of();
        if (c.getReviewStatus() == ReviewStatus.COMPLETED) {
            corrections = messages.findByConversationIdOrderByIdAsc(c.getId()).stream()
                    .filter(m -> m.getCorrectedContent() != null).map(m -> new GrammarCorrection(m.getId(),
                            m.getContent(), m.getCorrectedContent(), m.getFeedback()))
                    .toList();
            words = suggestions.findByConversationIdOrderByIdAsc(c.getId()).stream()
                    .map(s -> new WordSuggestionResponse(s.getId(), s.getWord(), s.getMeaning(), s.getExample(),
                            savedWords.findByUserIdAndWord(c.getUser().getId(), s.getWord()).map(SavedWord::getId)
                                    .orElse(null)))
                    .toList();
        }
        boolean active = c.getReviewStatus() == ReviewStatus.PROCESSING
                && ConversationMapper.processingActive(c, clock.instant());
        boolean canRetry = c.getReviewStatus() == ReviewStatus.FAILED
                || (c.getReviewStatus() == ReviewStatus.PROCESSING && !active);
        return new ReviewResponse(c.getId(), c.getReviewStatus(), c.getSummary(), c.getStrengths(), c.getWeaknesses(),
                c.getRecommendedLevel(), c.getLevelFeedback(), corrections, words, canRetry, active ? 3 : 0);
    }
}
