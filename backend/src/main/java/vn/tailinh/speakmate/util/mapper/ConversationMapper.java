package vn.tailinh.speakmate.util.mapper;

import java.time.Instant;
import java.util.List;
import vn.tailinh.speakmate.entity.*;
import vn.tailinh.speakmate.dto.response.conversation.*;
import vn.tailinh.speakmate.util.constant.MessageSender;

public final class ConversationMapper {

    private ConversationMapper() {
    }

    public static boolean processingActive(Conversation c, Instant now) {
        // Hết 90 giây thì cho thử lại, kể cả khi server khởi động lại giữa lúc gọi AI.
        return c.getProcessingToken() != null && c.getProcessingStartedAt() != null
                && c.getProcessingStartedAt().plusSeconds(90).isAfter(now);
    }

    public static MessageResponse message(Message m) {
        return new MessageResponse(m.getId(), m.getSender(), m.getContent(), m.getRequestId(), m.getCreatedAt());
    }

    public static ConversationSummary summary(Conversation c) {
        return new ConversationSummary(c.getId(), c.getTopic().getId(), c.getTopicNameSnapshot(),
                c.getProfileLevelSnapshot(), c.getLevel(), c.isOpen() ? "OPEN" : "CLOSED", c.getReviewStatus(),
                c.getCreatedAt(), c.getEndedAt());
    }

    public static ConversationDetail detail(Conversation c, List<Message> messages, Instant now) {
        Message pending = pending(messages);
        boolean active = processingActive(c, now);
        PendingReply reply = !c.isOpen() || pending == null ? null
                : new PendingReply(pending.getRequestId(), pending.getId(), active ? "PROCESSING" : "RETRYABLE",
                        active ? 3 : 0);
        return new ConversationDetail(c.getId(), c.getTopic().getId(), c.getTopicNameSnapshot(),
                c.getScenarioSnapshot(), c.getProfileLevelSnapshot(), c.getLevel(), c.isOpen() ? "OPEN" : "CLOSED",
                c.getReviewStatus(), c.getCreatedAt(), c.getEndedAt(),
                (int) messages.stream().filter(m -> m.getSender() == MessageSender.USER).count(), reply,
                messages.stream().map(ConversationMapper::message).toList());
    }

    // Các lượt được xử lý nối tiếp nên chỉ tin USER cuối có thể đang chờ lời AI.
    public static Message pending(List<Message> messages) {
        if (messages.isEmpty()) {
            return null;
        }
        Message last = messages.getLast();
        return last.getSender() == MessageSender.USER ? last : null;
    }

    public static TurnResponse turn(Conversation c, Message user, Message ai, String status, boolean replayed) {
        return new TurnResponse(c.getId(), user.getRequestId(), status, message(user), ai == null ? null : message(ai),
                status.equals("PROCESSING") ? 3 : 0, replayed);
    }
}
