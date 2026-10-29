package vn.tailinh.speakmate.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.tailinh.speakmate.entity.User;
import vn.tailinh.speakmate.exception.AuthException;
import vn.tailinh.speakmate.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // Lấy danh tính từ JWT đã xác thực, không lấy userId do client tự gửi.
    public CurrentIdentity getCurrentIdentity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw unauthenticated();
        }
        try {
            long userId = Long.parseLong(jwt.getSubject());
            Object claim = jwt.getClaims().get("auth_version");
            if (userId <= 0 || !(claim instanceof Number version)) {
                throw unauthenticated();
            }
            return new CurrentIdentity(userId, version.longValue());
        } catch (NumberFormatException exception) {
            throw unauthenticated();
        }
    }

    @Transactional(readOnly = true)
    public User getCurrentUser() {
        CurrentIdentity identity = getCurrentIdentity();
        User user = userRepository.findById(identity.userId()).orElseThrow(UserService::unauthenticated);
        if (!user.isActive() || user.getAuthVersion() != identity.authVersion()) {
            throw unauthenticated();
        }
        return user;
    }

    // Phải gọi trong transaction ghi; luôn khóa người dùng trước rồi mới khóa hội thoại.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public User lockCurrentUser() {
        return lockIdentity(getCurrentIdentity());
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public User lockIdentity(CurrentIdentity identity) {
        // Kiểm tra lại sau khi khóa vì tài khoản có thể vừa đăng xuất hoặc bị khóa.
        User user = userRepository.findByIdForUpdate(identity.userId()).orElseThrow(UserService::unauthenticated);
        if (!user.isActive() || user.getAuthVersion() != identity.authVersion()) {
            throw unauthenticated();
        }
        return user;
    }

    private static AuthException unauthenticated() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Phiên đăng nhập không hợp lệ");
    }

    public record CurrentIdentity(
        long userId,
        long authVersion) {
    }
}
