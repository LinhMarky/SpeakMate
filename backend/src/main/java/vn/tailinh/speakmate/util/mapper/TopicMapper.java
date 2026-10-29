package vn.tailinh.speakmate.util.mapper;

import vn.tailinh.speakmate.entity.Topic;
import vn.tailinh.speakmate.dto.response.topic.*;

public final class TopicMapper {

    private TopicMapper() {
    }

    public static TopicResponse toResponse(Topic topic) {
        return new TopicResponse(topic.getId(), topic.getName(), topic.getDescription(), topic.getLevel());
    }

    public static AdminTopicResponse toAdminResponse(Topic topic) {
        return new AdminTopicResponse(topic.getId(), topic.getName(), topic.getDescription(), topic.getLevel(),
                topic.getScenario(), topic.isActive());
    }
}
