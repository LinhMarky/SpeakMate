package vn.tailinh.speakmate.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tailinh.speakmate.config.AuthProperties;
import vn.tailinh.speakmate.entity.User;
import vn.tailinh.speakmate.repository.UserRepository;

/** Chỉ xử lý transaction DB ngắn; PasswordResetService phụ trách gửi email. */
@Service
@RequiredArgsConstructor
public class PasswordResetTransactionService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuthProperties authProperties;

    private final Clock clock;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public Optional<ResetDelivery> issueCode(String email) {
        User user = userRepository.findByEmailForUpdate(email).orElse(null);
        if (!canResetPassword(user)) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        if (user.getPasswordResetSentAt() != null
                && user.getPasswordResetSentAt().plus(authProperties.getResetResendCooldown()).isAfter(now)) {
            return Optional.empty();
        }

        String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        String codeHash = passwordEncoder.encode(code);
        user.setPasswordResetCodeHash(codeHash);
        user.setPasswordResetExpiresAt(now.plus(authProperties.getResetCodeTtl()));
        user.setPasswordResetSentAt(now);
        user.setPasswordResetAttempts(0);
        return Optional.of(new ResetDelivery(user.getId(), user.getEmail(), code, codeHash));
    }

    @Transactional
    public boolean verifyAndReset(String email, String code, String newPassword) {
        User user = userRepository.findByEmailForUpdate(email).orElse(null);
        Instant now = clock.instant();
        if (!canResetPassword(user) || user.getPasswordResetCodeHash() == null
                || user.getPasswordResetExpiresAt() == null || !user.getPasswordResetExpiresAt().isAfter(now)
                || user.getPasswordResetAttempts() >= authProperties.getResetMaxAttempts()) {
            return false;
        }

        if (!passwordEncoder.matches(code, user.getPasswordResetCodeHash())) {
            user.setPasswordResetAttempts(user.getPasswordResetAttempts() + 1);
            // Trả kết quả để số lần nhập sai được commit; service bên ngoài mới ném lỗi HTTP.
            return false;
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        clearCode(user);
        user.setRefreshToken(null);
        user.setRefreshExpiresAt(null);
        user.setAuthVersion(user.getAuthVersion() + 1);
        return true;
    }

    @Transactional
    public void invalidateUndeliveredCode(Long userId, String expectedHash) {
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        // Email cũ gửi lỗi không được xóa mã của yêu cầu đặt lại mới hơn.
        if (user != null && expectedHash.equals(user.getPasswordResetCodeHash())) {
            clearCode(user);
        }
    }

    private boolean canResetPassword(User user) {
        return user != null && user.isActive() && user.getPassword() != null && !user.getPassword().isBlank();
    }

    private void clearCode(User user) {
        user.setPasswordResetCodeHash(null);
        user.setPasswordResetExpiresAt(null);
        user.setPasswordResetAttempts(0);
        // Giữ sentAt để lỗi gửi email không cho phép bỏ qua thời gian chờ gửi lại.
    }

    /** Chỉ truyền nội bộ cho bộ gửi email, không trả qua API hoặc ghi log. */
    public record ResetDelivery(
        Long userId,
        String email,
        String code,
        String codeHash) {
    }
}
