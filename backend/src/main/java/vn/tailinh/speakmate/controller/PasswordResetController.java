package vn.tailinh.speakmate.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tailinh.speakmate.dto.request.auth.ForgotPasswordDTO;
import vn.tailinh.speakmate.dto.request.auth.ResetPasswordDTO;
import vn.tailinh.speakmate.dto.response.RestResponse;
import vn.tailinh.speakmate.service.PasswordResetService;

@RestController
@RequestMapping("/api/v1/auth/password")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/forgot")
    public ResponseEntity<RestResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordDTO request) {
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.accepted().body(RestResponse.success(202,
                "Nếu tài khoản đủ điều kiện, bạn sẽ nhận được email hướng dẫn đặt lại mật khẩu.", null));
    }

    @PostMapping("/reset")
    public ResponseEntity<RestResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordDTO request) {
        passwordResetService.resetPassword(request.getEmail(), request.getCode(), request.getNewPassword());
        return ResponseEntity
                .ok(RestResponse.success(200, "Đã đặt lại mật khẩu. Vui lòng đăng nhập bằng mật khẩu mới.", null));
    }
}
