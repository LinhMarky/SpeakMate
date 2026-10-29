package vn.tailinh.speakmate.service.ai;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.tailinh.speakmate.dto.response.conversation.MessageResponse;
import vn.tailinh.speakmate.exception.BusinessException;
import vn.tailinh.speakmate.util.constant.*;

class ReviewValidatorTest {

    final ReviewValidator validator = new ReviewValidator();

    final MessageResponse user = new MessageResponse(1L, MessageSender.USER, "I go yesterday", "id", Instant.now());

    final MessageResponse ai = new MessageResponse(2L, MessageSender.AI, "Where did you go?", "id", Instant.now());

    final AiConversation input = new AiConversation("Travel", "A conversation", EnglishLevel.A1, EnglishLevel.B2,
            List.of(user, ai));

    @Test
    void requiresBothThreeTurnsAndThirtyEnglishWords() {
        assertThat(validator.enoughData(input)).isFalse();
        var ten = new MessageResponse(3L, MessageSender.USER, "one two three four five six seven eight nine ten", "id",
                Instant.now());
        var shortTurn = new MessageResponse(4L, MessageSender.USER, "hi", "id", Instant.now());
        assertThat(validator.enoughData(with(List.of(ten, ten, ten)))).isTrue();
        assertThat(validator.enoughData(with(List.of(ten, ten, shortTurn)))).isFalse();
        assertThat(validator.enoughData(with(List.of(ten, ten)))).isFalse();
    }

    @Test
    void rejectsForeignAiAndDuplicateCorrectionIds() {
        assertThatCode(() -> validator
                .validate(report(List.of(new AiReview.Correction(1L, "I went yesterday.", "Past tense"))), input))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(report(List.of(new AiReview.Correction(2L, "x", "y"))), input))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate(report(List.of(new AiReview.Correction(999L, "x", "y"))), input))
                .isInstanceOf(BusinessException.class);
        var duplicate = new AiReview.Correction(1L, "x", "y");
        assertThatThrownBy(() -> validator.validate(report(List.of(duplicate, duplicate)), input))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void insufficientReportCannotInventStrengthsOrWords() {
        var valid = new AiReview(true, "Not enough data", null, null, null, null, List.of(), List.of());
        assertThatCode(() -> validator.validate(valid, input)).doesNotThrowAnyException();
        var invented = new AiReview(true, "Not enough data", "Good", null, null, null, List.of(), List.of());
        assertThatThrownBy(() -> validator.validate(invented, input)).isInstanceOf(BusinessException.class);
    }

    private AiConversation with(List<MessageResponse> transcript) {
        return new AiConversation("Travel", "A conversation", EnglishLevel.A1, EnglishLevel.B2, transcript);
    }

    private AiReview report(List<AiReview.Correction> corrections) {
        return new AiReview(false, "Summary", "Strengths", "Weaknesses", EnglishLevel.A1, "Practice A1", corrections,
                List.of());
    }
}
