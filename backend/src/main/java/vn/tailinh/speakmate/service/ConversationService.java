package vn.tailinh.speakmate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.tailinh.speakmate.dto.request.conversation.SendMessageRequest;
import vn.tailinh.speakmate.dto.response.conversation.TurnResponse;
import vn.tailinh.speakmate.service.ai.*;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationTransactionService transactions;

    private final AiClient ai;

    // Gọi AI ngoài transaction để không giữ khóa MySQL trong lúc chờ mạng.
    public TurnResponse send(long id, SendMessageRequest request) {
        var attempt = transactions.claimReply(id, request);
        if (attempt.existing() != null) {
            return attempt.existing();
        }
        try {
            String reply = ai.reply(attempt.input());
            if (reply == null || reply.isBlank() || reply.trim().length() > 4000) {
                throw AiFailure.invalid();
            }
            return transactions.completeReply(attempt, reply.trim());
        } catch (RuntimeException exception) {
            try {
                transactions.failReply(attempt);
            } catch (RuntimeException ignored) {
                // Khóa xử lý sẽ hết hạn để người dùng có thể thử lại.
            }
            throw exception;
        }
    }
}
