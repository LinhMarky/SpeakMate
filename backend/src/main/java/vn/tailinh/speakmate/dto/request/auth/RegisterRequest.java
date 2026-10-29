package vn.tailinh.speakmate.dto.request.auth;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.tailinh.speakmate.util.constant.EnglishLevel;

@Getter
@Setter
public class RegisterRequest {

    @NotBlank(message = "Tên không được để trống")
    @Size(max = 100, message = "Tên tối đa 100 ký tự")
    private String name;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(max = 64, message = "Mật khẩu tối đa 64 ký tự")
    private String password;

    private EnglishLevel level;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }
}
