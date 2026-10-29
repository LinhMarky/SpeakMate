package vn.tailinh.speakmate.dto.response.word;

import java.util.List;

public record SaveSuggestionsResponse(
    List<Item> created,
    List<Item> existing) {

    public record Item(
        Long suggestionId,
        SavedWordResponse savedWord) {
    }
}
