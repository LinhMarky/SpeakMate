package vn.tailinh.speakmate.service;

import java.time.Clock;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.tailinh.speakmate.dto.request.auth.LoginRequest;
import vn.tailinh.speakmate.dto.request.auth.RegisterRequest;
import vn.tailinh.speakmate.dto.response.auth.TokenResponse;
import vn.tailinh.speakmate.dto.response.auth.UserResponse;
import vn.tailinh.speakmate.entity.User;
import vn.tailinh.speakmate.exception.AuthException;
import vn.tailinh.speakmate.repository.UserRepository;
import vn.tailinh.speakmate.service.google.GoogleIdentity;
import vn.tailinh.speakmate.util.constant.EnglishLevel;
import vn.tailinh.speakmate.util.constant.Role;
import vn.tailinh.speakmate.util.mapper.UserMapper;

@Service
@RequiredArgsConstructor
public class AuthTransactionService {

    // Vẫn chạy BCrypt khi email không tồn tại để giảm chênh lệch thời gian kiểm tra.
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final PasswordPolicy passwordPolicy;

    private final TokenService tokenService;

    private final Clock clock;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        passwordPolicy.validateNewPassword(request.getPassword());
        String email = normalizeEmail(request.getEmail());
        if (userRepository.findByEmail(email).isPresent()) {
            throw conflict("EMAIL_ALREADY_USED", "Email đã được sử dụng");
        }
        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        user.setActive(true);
        user.setLevel(request.getLevel() == null ? EnglishLevel.A1 : request.getLevel());
        // UNIQUE(email) vẫn chặn trùng nếu hai yêu cầu đăng ký chạy đồng thời.
        return UserMapper.toResponse(userRepository.saveAndFlush(user));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailForUpdate(normalizeEmail(request.getEmail())).orElse(null);
        boolean canVerify = passwordPolicy.canVerify(request.getPassword());
        String passwordToCheck = canVerify ? request.getPassword() : "invalid-password";
        String hashToCheck = user == null || user.getPassword() == null ? DUMMY_PASSWORD_HASH : user.getPassword();
        boolean matches = passwordEncoder.matches(passwordToCheck, hashToCheck);
        if (!canVerify || user == null || user.getPassword() == null || !matches) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng");
        }
        requireActive(user);
        return createSession(user);
    }

    @Transactional
    public TokenResponse loginWithGoogle(GoogleIdentity identity) {
        // Khóa ngay lần đọc đầu để JPA không giữ phiên đăng nhập đã cũ.
        User existing = userRepository.findByGoogleSubjectForUpdate(identity.subject()).orElse(null);
        if (existing != null) {
            requireActive(existing);
            // Google subject là mã định danh ổn định; không ghi đè email tài khoản.
            return createSession(existing);
        }

        String email = normalizeEmail(identity.email());
        if (userRepository.findByEmailForUpdate(email).isPresent()) {
            throw conflict("GOOGLE_LINK_REQUIRED",
                    "Email đã có tài khoản. Hãy đăng nhập rồi liên kết Google trong tài khoản");
        }
        User user = new User();
        String name = identity.name() == null ? "Người học" : identity.name().trim();
        int end = name.offsetByCodePoints(0, Math.min(name.codePointCount(0, name.length()), 100));
        user.setName(name.isBlank() ? "Người học" : name.substring(0, end));
        user.setEmail(email);
        user.setGoogleSubject(identity.subject());
        user.setPassword(null);
        user.setLevel(EnglishLevel.A1);
        user.setRole(Role.USER);
        user.setActive(true);
        userRepository.saveAndFlush(user);
        return createSession(user);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        long userId = tokenService.refreshUserId(refreshToken);
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(AuthTransactionService::invalidSession);
        if (!user.isActive() || user.getRefreshExpiresAt() == null
                || !user.getRefreshExpiresAt().isAfter(clock.instant())
                || !tokenService.matchesToken(refreshToken, user.getRefreshToken())) {
            throw invalidSession();
        }
        // Khóa dòng để một refresh token chỉ được đổi một lần; giữ nguyên phiên hiện tại.
        return issueTokens(user);
    }

    @Transactional
    public void logout(UserService.CurrentIdentity identity) {
        User user = lockCurrentUser(identity);
        user.setAuthVersion(user.getAuthVersion() + 1);
        user.setRefreshToken(null);
        user.setRefreshExpiresAt(null);
    }

    @Transactional
    public UserResponse linkGoogle(UserService.CurrentIdentity currentIdentity, GoogleIdentity googleIdentity) {
        User user = lockCurrentUser(currentIdentity);
        if (!user.getEmail().equals(normalizeEmail(googleIdentity.email()))) {
            throw conflict("GOOGLE_EMAIL_MISMATCH", "Email Google phải trùng email tài khoản hiện tại");
        }
        if (user.getGoogleSubject() != null && !user.getGoogleSubject().equals(googleIdentity.subject())) {
            throw conflict("GOOGLE_ALREADY_LINKED", "Tài khoản đã liên kết với một tài khoản Google khác");
        }
        User linkedUser = userRepository.findByGoogleSubject(googleIdentity.subject()).orElse(null);
        if (linkedUser != null && !linkedUser.getId().equals(user.getId())) {
            throw conflict("GOOGLE_ALREADY_LINKED", "Tài khoản Google đã được liên kết");
        }
        user.setGoogleSubject(googleIdentity.subject());
        userRepository.saveAndFlush(user);
        return UserMapper.toResponse(user);
    }

    private User lockCurrentUser(UserService.CurrentIdentity identity) {
        User user = userRepository.findByIdForUpdate(identity.userId())
                .orElseThrow(AuthTransactionService::invalidSession);
        if (!user.isActive() || user.getAuthVersion() != identity.authVersion()) {
            throw invalidSession();
        }
        return user;
    }

    private TokenResponse createSession(User user) {
        // Mỗi tài khoản có một phiên: đăng nhập mới sẽ vô hiệu các token cũ.
        user.setAuthVersion(user.getAuthVersion() + 1);
        return issueTokens(user);
    }

    private TokenResponse issueTokens(User user) {
        String refreshToken = tokenService.newRefreshToken(user.getId());
        user.setRefreshToken(tokenService.hashToken(refreshToken));
        user.setRefreshExpiresAt(tokenService.refreshExpiry());

        TokenResponse response = new TokenResponse();
        response.setAccessToken(tokenService.createAccessToken(user));
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(tokenService.accessExpiresInSeconds());
        response.setUser(UserMapper.toResponse(user));
        return response;
    }

    private static void requireActive(User user) {
        if (!user.isActive()) {
            throw new AuthException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Tài khoản đã bị khóa");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }

    private static AuthException invalidSession() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION",
                "Phiên đăng nhập không hợp lệ hoặc đã hết hạn");
    }
}
