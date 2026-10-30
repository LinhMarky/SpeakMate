package vn.tailinh.speakmate.dto.request;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import vn.tailinh.speakmate.util.constant.EnglishLevel;

public final class AdminRequests {
    private AdminRequests() {}

    public record Active(@NotNull Boolean active) {}

    public record Password(@NotBlank @Size(max = 256) String currentPassword,
            @NotBlank @Size(min = 8, max = 64) String newPassword) {}

    public record Deck(@NotBlank @Size(max = 100) String name, @NotNull EnglishLevel level,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid Word> words) {}

    public record Word(@NotBlank @Size(max = 100) String word,
            @NotBlank @Size(max = 255) String meaning, @Size(max = 1000) String example) {}
}
