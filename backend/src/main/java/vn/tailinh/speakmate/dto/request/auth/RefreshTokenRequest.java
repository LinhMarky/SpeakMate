package vn.tailinh.speakmate.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefreshTokenRequest {

    @NotBlank(message = "Thiếu refresh token")
    @Size(max = 512, message = "Refresh token quá dài")
    private String refreshToken;
}
