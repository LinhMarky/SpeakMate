package vn.tailinh.speakmate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tailinh.speakmate.dto.request.AdminRequests;
import vn.tailinh.speakmate.dto.response.PageResponse;
import vn.tailinh.speakmate.dto.response.auth.UserResponse;
import vn.tailinh.speakmate.entity.User;
import vn.tailinh.speakmate.exception.BusinessException;
import vn.tailinh.speakmate.repository.*;
import vn.tailinh.speakmate.util.constant.Role;
import vn.tailinh.speakmate.util.mapper.UserMapper;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserService currentUser;
    private final UserRepository users;
    private final TopicRepository topics;
    private final ConversationRepository conversations;
    private final SystemDeckRepository decks;
    private final PasswordEncoder passwords;
    private final PasswordPolicy policy;

    public record Statistics(long users, long topics, long systemDecks, long completedSessions) {}

    @Transactional(readOnly = true)
    public Statistics statistics() {
        requireAdmin(currentUser.getCurrentUser());
        return new Statistics(users.count(), topics.count(), decks.count(), conversations.countFinished());
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(int page, int size, String search, Boolean active) {
        requireAdmin(currentUser.getCurrentUser());
        return PageResponse.from(users.searchAdmin(search.trim(), active,
                PageResponse.request(page, size, Sort.by(Sort.Direction.DESC, "id"))).map(UserMapper::toResponse));
    }

    @Transactional
    public UserResponse setActive(long id, boolean active) {
        User admin = currentUser.lockCurrentUser();
        requireAdmin(admin);
        if (admin.getId() == id) throw invalid("Không được tự khóa tài khoản đang dùng.");
        if (users.findById(id).orElseThrow(BusinessException::notFound).getRole() == Role.ADMIN) {
            throw invalid("Không khóa tài khoản quản trị ở chức năng này.");
        }
        User user = users.findByIdForUpdate(id).orElseThrow(BusinessException::notFound);
        // Giữ quyền quản trị khả dụng; màn này chỉ khóa/mở tài khoản người học.
        if (user.getRole() == Role.ADMIN) throw invalid("Không khóa tài khoản quản trị ở chức năng này.");
        if (user.isActive() != active) {
            user.setActive(active);
            user.setAuthVersion(user.getAuthVersion() + 1);
            user.setRefreshToken(null);
            user.setRefreshExpiresAt(null);
        }
        return UserMapper.toResponse(user);
    }

    @Transactional
    public void changePassword(AdminRequests.Password request) {
        User user = currentUser.lockCurrentUser();
        requireAdmin(user);
        if (user.getPassword() == null || !policy.canVerify(request.currentPassword())
                || !passwords.matches(request.currentPassword(), user.getPassword())) {
            throw invalid("Mật khẩu hiện tại không đúng.");
        }
        policy.validateNewPassword(request.newPassword());
        if (passwords.matches(request.newPassword(), user.getPassword())) throw invalid("Mật khẩu mới phải khác mật khẩu cũ.");
        user.setPassword(passwords.encode(request.newPassword()));
        user.setAuthVersion(user.getAuthVersion() + 1);
        user.setRefreshToken(null);
        user.setRefreshExpiresAt(null);
        user.setPasswordResetCodeHash(null);
        user.setPasswordResetExpiresAt(null);
        user.setPasswordResetAttempts(0);
    }

    static void requireAdmin(User user) {
        if (user.getRole() != Role.ADMIN) throw new AccessDeniedException("ADMIN required");
    }

    private BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
