package vn.tailinh.speakmate.dto.request.word;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record SaveGeneratedWordsRequest(
    @NotNull(message = "Danh sách từ không được null")
    @Size(min = 1, max = 20, message = "Số lượng từ lưu từ 1 đến 20")
    List<@NotNull @Valid WordItem> words) {

    public record WordItem(
        @NotBlank(message = "Từ vựng không được để trống")
        @Size(min = 1, max = 100, message = "Từ vựng tối đa 100 ký tự")
        String word,

        @NotBlank(message = "Nghĩa không được để trống")
        @Size(min = 1, max = 255, message = "Nghĩa tối đa 255 ký tự")
        String meaning,

        @Size(max = 1000, message = "Ví dụ tối đa 1000 ký tự")
        String example) {
    }
}

