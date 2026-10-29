package vn.tailinh.speakmate.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import vn.tailinh.speakmate.dto.request.auth.GoogleLoginRequest;
import vn.tailinh.speakmate.dto.request.auth.LoginRequest;
import vn.tailinh.speakmate.dto.request.auth.RefreshTokenRequest;
import vn.tailinh.speakmate.dto.request.auth.RegisterRequest;
import vn.tailinh.speakmate.dto.response.RestResponse;
import vn.tailinh.speakmate.dto.response.auth.TokenResponse;
import vn.tailinh.speakmate.dto.response.auth.UserResponse;
import vn.tailinh.speakmate.service.AuthService;
import vn.tailinh.speakmate.service.UserService;
import vn.tailinh.speakmate.util.mapper.UserMapper;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<RestResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RestResponse.success(201, "Đăng ký thành công. Hãy đăng nhập để tiếp tục", user));
    }

    @PostMapping("/login")
    public RestResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return RestResponse.success(200, "Đăng nhập thành công", authService.login(request));
    }

    @PostMapping("/google")
    public RestResponse<TokenResponse> google(@Valid @RequestBody GoogleLoginRequest request) {
        return RestResponse.success(200, "Đăng nhập Google thành công",
                authService.loginWithGoogle(request.getIdToken()));
    }

    @PostMapping("/refresh")
    public RestResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return RestResponse.success(200, "Đã cấp token mới", authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public RestResponse<Void> logout() {
        authService.logout();
        return RestResponse.success(200, "Đăng xuất thành công", null);
    }

    @PostMapping("/google/link")
    public RestResponse<UserResponse> linkGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return RestResponse.success(200, "Liên kết Google thành công", authService.linkGoogle(request.getIdToken()));
    }

    @GetMapping("/me")
    public RestResponse<UserResponse> me() {
        return RestResponse.success(200, "Thông tin tài khoản", UserMapper.toResponse(userService.getCurrentUser()));
    }
}
