package vn.tailinh.speakmate.util.mapper;

import vn.tailinh.speakmate.dto.response.word.SavedWordResponse;
import vn.tailinh.speakmate.entity.SavedWord;

public final class SavedWordMapper {

    private SavedWordMapper() {
    }

    public static SavedWordResponse toResponse(SavedWord word) {
        return new SavedWordResponse(word.getId(), word.getWord(), word.getMeaning(), word.getExample(),
                word.getStatus(), word.getLastReviewedAt(), word.getCreatedAt());
    }
}
