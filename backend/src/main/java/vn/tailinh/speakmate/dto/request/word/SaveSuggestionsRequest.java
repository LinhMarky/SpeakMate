package vn.tailinh.speakmate.dto.request.word;

import java.util.List;
import jakarta.validation.constraints.*;

public record SaveSuggestionsRequest(
    @NotNull
    @Size(min = 1, max = 10)
    List<@NotNull @Positive Long> suggestionIds) {
}
