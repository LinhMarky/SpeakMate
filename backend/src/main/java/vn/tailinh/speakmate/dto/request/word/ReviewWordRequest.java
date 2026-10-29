package vn.tailinh.speakmate.dto.request.word;

import jakarta.validation.constraints.NotNull;
import vn.tailinh.speakmate.util.constant.SavedWordStatus;

public record ReviewWordRequest(
    @NotNull
    SavedWordStatus status) {
}
