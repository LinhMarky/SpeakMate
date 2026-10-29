package vn.tailinh.speakmate.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tailinh.speakmate.dto.request.conversation.*;
import vn.tailinh.speakmate.dto.response.PageResponse;
import vn.tailinh.speakmate.dto.response.conversation.*;
import vn.tailinh.speakmate.entity.*;
import vn.tailinh.speakmate.exception.BusinessException;
import vn.tailinh.speakmate.repository.*;
import vn.tailinh.speakmate.service.ai.AiConversation;
import vn.tailinh.speakmate.util.constant.MessageSender;
import vn.tailinh.speakmate.util.mapper.ConversationMapper;

@Service
@RequiredArgsConstructor
public class ConversationTransactionService {

    private final ConversationRepository conversations;

    private final MessageRepository messages;

    private final TopicRepository topics;

    private final UserService users;

    private final Clock clock;

    public record Created(
        ConversationDetail detail,
        boolean replayed) {
    }

    public record ReplyAttempt(
        Long conversationId,
        String requestId,
        String token,
        UserService.CurrentIdentity identity,
        AiConversation input,
        TurnResponse existing) {
    }

    @Transactional
    public Created create(CreateConversationRequest request) {
        User user = users.lockCurrentUser();
        Conversation old = conversations.findByUserIdAndCreateRequestId(user.getId(), request.createRequestId())
                .orElse(null);
        if (old != null) {
            if (!old.getTopic().getId().equals(request.topicId()) || old.getLevel() != request.level()) {
                throw conflict();
            }
            return new Created(detail(old), true);
        }
        Topic topic = topics.findByIdForUpdate(request.topicId()).orElseThrow(BusinessException::notFound);
        if (!topic.isActive()) {
            throw BusinessException.conflict("TOPIC_INACTIVE", "Chủ đề đã tạm ngừng");
        }
        Conversation c = new Conversation();
        // Lưu bản chụp để bài cũ không đổi khi admin sửa chủ đề hoặc người học đổi trình độ.
        c.setUser(user);
        c.setTopic(topic);
        c.setCreateRequestId(request.createRequestId());
        c.setProfileLevelSnapshot(user.getLevel());
        c.setLevel(request.level());
        c.setTopicNameSnapshot(topic.getName());
        c.setScenarioSnapshot(topic.getScenario());
        conversations.saveAndFlush(c);
        return new Created(detail(c), false);
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationSummary> list(int page, int size) {
        long userId = users.getCurrentUser().getId();
        return PageResponse.from(
                conversations.findByUserId(userId, PageResponse.request(page, size, Sort.by(Sort.Direction.DESC, "id")))
                        .map(ConversationMapper::summary));
    }

    @Transactional(readOnly = true)
    public ConversationDetail get(long id) {
        return detail(conversations.findByIdAndUserId(id, users.getCurrentUser().getId())
                .orElseThrow(BusinessException::notFound));
    }

    // Bước 1: lưu câu người học và mã xử lý; commit xong mới gọi AI.
    @Transactional
    public ReplyAttempt claimReply(long id, SendMessageRequest request) {
        UserService.CurrentIdentity identity = users.getCurrentIdentity();
        User user = users.lockIdentity(identity);
        Conversation c = conversations.findOwnedForUpdate(id, user.getId()).orElseThrow(BusinessException::notFound);
        List<Message> transcript = messages.findByConversationIdOrderByIdAsc(id);
        Message old = transcript.stream()
                .filter(m -> m.getSender() == MessageSender.USER && m.getRequestId().equals(request.requestId()))
                .findFirst().orElse(null);
        if (old != null) {
            // Cùng UUID thì trả lại lượt cũ, không tạo thêm tin nhắn.
            if (!old.getContent().equals(request.content())) {
                throw conflict();
            }
            Message ai = transcript.stream()
                    .filter(m -> m.getSender() == MessageSender.AI && m.getRequestId().equals(request.requestId()))
                    .findFirst().orElse(null);
            if (ai != null) {
                return existing(c, old, ai, "COMPLETED", identity);
            }
            if (!c.isOpen()) {
                return existing(c, old, null, "CANCELLED", identity);
            }
            if (ConversationMapper.processingActive(c, clock.instant())) {
                return existing(c, old, null, "PROCESSING", identity);
            }
        } else {
            if (!c.isOpen()) {
                throw BusinessException.conflict("CONVERSATION_CLOSED", "Bài hội thoại đã kết thúc");
            }
            if (ConversationMapper.pending(transcript) != null) {
                throw BusinessException.conflict("TURN_PENDING", "Hãy thử lại lượt đang chờ hoặc kết thúc bài");
            }
            if (transcript.stream().filter(m -> m.getSender() == MessageSender.USER).count() >= 20) {
                throw BusinessException.conflict("TURN_LIMIT_REACHED",
                        "Bài đã đủ 20 lượt. Hãy kết thúc để xem nhận xét");
            }
            Message message = new Message();
            message.setConversation(c);
            message.setSender(MessageSender.USER);
            message.setContent(request.content());
            message.setRequestId(request.requestId());
            transcript.add(messages.saveAndFlush(message));
        }
        String token = UUID.randomUUID().toString();
        c.setProcessingToken(token);
        c.setProcessingStartedAt(clock.instant());
        AiConversation input = new AiConversation(c.getTopicNameSnapshot(), c.getScenarioSnapshot(),
                c.getProfileLevelSnapshot(), c.getLevel(),
                transcript.stream().map(ConversationMapper::message).toList());
        return new ReplyAttempt(id, request.requestId(), token, identity, input, null);
    }

    // Bước 2: chỉ mã xử lý hiện tại được lưu lời AI; kết thúc/thử lại sẽ vô hiệu mã cũ.
    @Transactional
    public TurnResponse completeReply(ReplyAttempt attempt, String content) {
        users.lockIdentity(attempt.identity());
        Conversation c = conversations.findOwnedForUpdate(attempt.conversationId(), attempt.identity().userId())
                .orElseThrow(BusinessException::notFound);
        List<Message> transcript = messages.findByConversationIdOrderByIdAsc(c.getId());
        Message user = transcript.stream()
                .filter(m -> m.getSender() == MessageSender.USER && m.getRequestId().equals(attempt.requestId()))
                .findFirst().orElseThrow();
        Message oldAi = transcript.stream()
                .filter(m -> m.getSender() == MessageSender.AI && m.getRequestId().equals(attempt.requestId()))
                .findFirst().orElse(null);
        if (oldAi != null) {
            return ConversationMapper.turn(c, user, oldAi, "COMPLETED", true);
        }
        if (!c.isOpen()) {
            return ConversationMapper.turn(c, user, null, "CANCELLED", false);
        }
        if (!attempt.token().equals(c.getProcessingToken())) {
            throw BusinessException.conflict("TURN_PENDING", "Lượt đã được thay bằng lần thử mới. Hãy tải lại bài");
        }
        Message ai = new Message();
        ai.setConversation(c);
        ai.setSender(MessageSender.AI);
        ai.setRequestId(attempt.requestId());
        ai.setContent(content);
        messages.saveAndFlush(ai);
        c.clearProcessing();
        return ConversationMapper.turn(c, user, ai, "COMPLETED", false);
    }

    @Transactional
    public void failReply(ReplyAttempt attempt) {
        users.lockIdentity(attempt.identity());
        Conversation c = conversations.findOwnedForUpdate(attempt.conversationId(), attempt.identity().userId())
                .orElseThrow(BusinessException::notFound);
        if (c.isOpen() && attempt.token().equals(c.getProcessingToken())) {
            c.clearProcessing();
        }
    }

    private ReplyAttempt existing(Conversation c, Message user, Message ai, String status,
            UserService.CurrentIdentity identity) {
        return new ReplyAttempt(c.getId(), user.getRequestId(), null, identity, null,
                ConversationMapper.turn(c, user, ai, status, true));
    }

    private ConversationDetail detail(Conversation c) {
        return ConversationMapper.detail(c, messages.findByConversationIdOrderByIdAsc(c.getId()), clock.instant());
    }

    private BusinessException conflict() {
        return BusinessException.conflict("REQUEST_CONFLICT", "UUID đã được dùng với dữ liệu khác");
    }
}
