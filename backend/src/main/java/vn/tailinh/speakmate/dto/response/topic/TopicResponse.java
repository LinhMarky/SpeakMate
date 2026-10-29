package vn.tailinh.speakmate.dto.response.topic;

import vn.tailinh.speakmate.util.constant.EnglishLevel;

public record TopicResponse(
    Long id,
    String name,
    String description,
    EnglishLevel level) {
}
