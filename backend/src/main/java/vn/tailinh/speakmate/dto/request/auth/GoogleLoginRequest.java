package vn.tailinh.speakmate.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleLoginRequest {

    @NotBlank(message = "Thiếu Google ID token")
    @Size(max = 10000, message = "Google ID token quá dài")
    private String idToken;
}
