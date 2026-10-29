package vn.tailinh.speakmate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tailinh.speakmate.dto.request.topic.TopicRequest;
import vn.tailinh.speakmate.dto.response.PageResponse;
import vn.tailinh.speakmate.dto.response.topic.*;
import vn.tailinh.speakmate.entity.Topic;
import vn.tailinh.speakmate.exception.BusinessException;
import vn.tailinh.speakmate.repository.TopicRepository;
import vn.tailinh.speakmate.util.constant.Role;
import vn.tailinh.speakmate.util.mapper.TopicMapper;

@Service
@RequiredArgsConstructor
public class TopicService {

    private final TopicRepository topics;

    private final UserService users;

    @Transactional(readOnly = true)
    public PageResponse<TopicResponse> list(int page, int size) {
        return PageResponse.from(topics.findByActiveTrue(PageResponse.request(page, size, Sort.by("name", "id")))
                .map(TopicMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminTopicResponse> listAdmin(int page, int size) {
        if (users.getCurrentUser().getRole() != Role.ADMIN) {
            throw new AccessDeniedException("ADMIN required");
        }
        return PageResponse.from(topics.findAll(PageResponse.request(page, size, Sort.by(Sort.Direction.DESC, "id")))
                .map(TopicMapper::toAdminResponse));
    }

    @Transactional
    public AdminTopicResponse create(TopicRequest request) {
        lockAdmin();
        Topic topic = new Topic();
        apply(topic, request);
        return TopicMapper.toAdminResponse(topics.saveAndFlush(topic));
    }

    @Transactional
    public AdminTopicResponse update(long id, TopicRequest request) {
        lockAdmin();
        Topic topic = topics.findByIdForUpdate(id).orElseThrow(BusinessException::notFound);
        apply(topic, request);
        return TopicMapper.toAdminResponse(topics.saveAndFlush(topic));
    }

    @Transactional
    public AdminTopicResponse setActive(long id, boolean active) {
        // Tắt chủ đề chỉ ngừng tạo bài mới, lịch sử hội thoại vẫn được giữ.
        lockAdmin();
        Topic topic = topics.findByIdForUpdate(id).orElseThrow(BusinessException::notFound);
        topic.setActive(active);
        return TopicMapper.toAdminResponse(topic);
    }

    private void lockAdmin() {
        if (users.lockCurrentUser().getRole() != Role.ADMIN) {
            throw new AccessDeniedException("ADMIN required");
        }
    }

    private void apply(Topic topic, TopicRequest request) {
        topic.setName(request.name());
        topic.setDescription(request.description());
        topic.setLevel(request.level());
        topic.setScenario(request.scenario());
    }
}
