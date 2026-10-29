package vn.tailinh.speakmate.service;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.tailinh.speakmate.exception.AuthException;

@Component
public class PasswordPolicy {

    public void validateNewPassword(String password) {
        if (!canVerify(password) || password.length() < 8 || password.isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD",
                    "Mật khẩu cần 8–64 ký tự và tối đa 72 byte UTF-8.");
        }
    }

    public boolean canVerify(String password) {
        return password != null && !password.isEmpty() && password.length() <= 64
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
