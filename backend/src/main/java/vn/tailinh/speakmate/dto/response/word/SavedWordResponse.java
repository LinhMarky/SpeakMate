package vn.tailinh.speakmate.dto.response.word;

import java.time.Instant;
import vn.tailinh.speakmate.util.constant.SavedWordStatus;

public record SavedWordResponse(
    Long id,
    String word,
    String meaning,
    String example,
    SavedWordStatus status,
    Instant lastReviewedAt,
    Instant createdAt) {
}
