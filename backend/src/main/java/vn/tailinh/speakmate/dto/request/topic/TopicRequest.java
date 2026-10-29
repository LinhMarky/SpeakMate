package vn.tailinh.speakmate.dto.request.topic;

import jakarta.validation.constraints.*;
import vn.tailinh.speakmate.util.constant.EnglishLevel;

public record TopicRequest(
    @NotBlank
    @Size(max = 100)
    String name,
    @Size(max = 2000)
    String description,
    @NotNull
    EnglishLevel level,
    @NotBlank
    @Size(max = 4000)
    String scenario) {

    public TopicRequest {
        name = name == null ? null : name.trim();
        description = description == null ? null : description.trim();
        scenario = scenario == null ? null : scenario.trim();
    }
}
