package vn.tailinh.speakmate.dto.response.topic;

import vn.tailinh.speakmate.util.constant.EnglishLevel;

public record AdminTopicResponse(
    Long id,
    String name,
    String description,
    EnglishLevel level,
    String scenario,
    boolean active) {
}
