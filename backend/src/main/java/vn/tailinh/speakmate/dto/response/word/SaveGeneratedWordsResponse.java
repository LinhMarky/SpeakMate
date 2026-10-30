package vn.tailinh.speakmate.dto.response.word;

import java.util.List;

public record SaveGeneratedWordsResponse(
    List<SavedWordResponse> created,
    List<SavedWordResponse> existing) {
}

