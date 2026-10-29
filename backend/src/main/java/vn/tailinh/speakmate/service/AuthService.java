package vn.tailinh.speakmate.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import vn.tailinh.speakmate.dto.request.auth.LoginRequest;
import vn.tailinh.speakmate.dto.request.auth.RegisterRequest;
import vn.tailinh.speakmate.dto.response.auth.TokenResponse;
import vn.tailinh.speakmate.dto.response.auth.UserResponse;
import vn.tailinh.speakmate.service.google.GoogleIdentity;
import vn.tailinh.speakmate.service.google.GoogleIdentityVerifier;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthTransactionService authTransactionService;

    private final GoogleIdentityVerifier googleIdentityVerifier;

    private final UserService userService;

    public UserResponse register(RegisterRequest request) {
        return authTransactionService.register(request);
    }

    public TokenResponse login(LoginRequest request) {
        return authTransactionService.login(request);
    }

    public TokenResponse loginWithGoogle(String idToken) {
        // Xác thực Google có thể cần mạng, nên thực hiện trước transaction DB.
        GoogleIdentity identity = googleIdentityVerifier.verify(idToken);
        return authTransactionService.loginWithGoogle(identity);
    }

    public TokenResponse refresh(String refreshToken) {
        return authTransactionService.refresh(refreshToken);
    }

    public void logout() {
        authTransactionService.logout(userService.getCurrentIdentity());
    }

    public UserResponse linkGoogle(String idToken) {
        UserService.CurrentIdentity currentIdentity = userService.getCurrentIdentity();
        GoogleIdentity googleIdentity = googleIdentityVerifier.verify(idToken);
        return authTransactionService.linkGoogle(currentIdentity, googleIdentity);
    }
}
