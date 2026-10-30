package vn.tailinh.speakmate.dto.request.word;

import jakarta.validation.constraints.*;

public record GenerateVocabularyRequest(
    @NotBlank(message = "Chủ đề không được để trống")
    @Size(min = 1, max = 200, message = "Chủ đề từ 1 đến 200 ký tự")
    String topic,

    @NotNull(message = "Trình độ không được để trống")
    @Pattern(regexp = "A1|A2|B1|B2|C1|C2", message = "Trình độ phải là A1, A2, B1, B2, C1 hoặc C2")
    String level,

    @NotNull(message = "Số lượng từ không được để trống")
    @Min(value = 5, message = "Số lượng từ tối thiểu là 5")
    @Max(value = 20, message = "Số lượng từ tối đa là 20")
    Integer count) {
}

