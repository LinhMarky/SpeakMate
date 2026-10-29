package vn.tailinh.speakmate.dto.request.conversation;

import jakarta.validation.constraints.*;

public record SendMessageRequest(
    @NotBlank
    @Size(max = 1000)
    String content,
    @NotNull
    @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    String requestId) {

    public SendMessageRequest {
        content = content == null ? null : content.trim();
    }
}
