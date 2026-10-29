package vn.tailinh.speakmate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.tailinh.speakmate.dto.response.conversation.ReviewResponse;
import vn.tailinh.speakmate.service.ai.AiClient;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewTransactionService transactions;

    private final AiClient ai;

    // Đóng bài trong transaction ngắn, gọi AI bên ngoài, sau đó lưu báo cáo.
    public ReviewResponse finish(long id, boolean retry) {
        var attempt = transactions.claim(id, retry);
        if (attempt.existing() != null) {
            return attempt.existing();
        }
        try {
            return transactions.complete(attempt, ai.review(attempt.input()));
        } catch (RuntimeException exception) {
            try {
                transactions.fail(attempt);
            } catch (RuntimeException ignored) {
                // Mã xử lý hết hạn vẫn cho phép người dùng yêu cầu thử lại.
            }
            if (exception instanceof org.springframework.dao.DataIntegrityViolationException) {
                throw vn.tailinh.speakmate.service.ai.AiFailure.invalid();
            }
            throw exception;
        }
    }
}
