package vn.tailinh.speakmate.dto.request.topic;

import jakarta.validation.constraints.NotNull;

public record TopicActiveRequest(
    @NotNull
    Boolean active) {
}
