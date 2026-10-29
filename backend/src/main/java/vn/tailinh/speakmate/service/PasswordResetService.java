package vn.tailinh.speakmate.service;

import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.tailinh.speakmate.config.AuthProperties;
import vn.tailinh.speakmate.exception.AuthException;
import vn.tailinh.speakmate.service.PasswordResetTransactionService.ResetDelivery;
import vn.tailinh.speakmate.service.mail.PasswordResetMailer;

@Service
@Slf4j
public class PasswordResetService {

    private final PasswordResetTransactionService transactions;

    private final PasswordResetMailer mailer;

    private final PasswordPolicy passwordPolicy;

    private final AuthProperties authProperties;

    private final TaskExecutor executor;

    public PasswordResetService(PasswordResetTransactionService transactions, PasswordResetMailer mailer,
            PasswordPolicy passwordPolicy, AuthProperties authProperties,
            @Qualifier("passwordResetExecutor") TaskExecutor executor) {
        this.transactions = transactions;
        this.mailer = mailer;
        this.passwordPolicy = passwordPolicy;
        this.authProperties = authProperties;
        this.executor = executor;
    }

    public void requestReset(String email) {
        // Kiểm tra cấu hình trước khi tìm tài khoản để không lộ email có tồn tại hay không.
        if (!mailer.isConfigured()) {
            throw unavailable();
        }
        String normalizedEmail = normalizeEmail(email);
        try {
            // Xử lý gửi email ở nền; mọi email đều nhận cùng phản hồi HTTP.
            executor.execute(() -> sendResetEmail(normalizedEmail));
        } catch (RejectedExecutionException exception) {
            throw unavailable();
        }
    }

    public void resetPassword(String email, String code, String newPassword) {
        passwordPolicy.validateNewPassword(newPassword);
        boolean changed = transactions.verifyAndReset(normalizeEmail(email), code, newPassword);
        if (!changed) {
            // Ném lỗi ngoài transaction để không rollback số lần nhập sai mã.
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_RESET_CODE",
                    "Mã không hợp lệ hoặc đã hết hạn. Hãy yêu cầu mã mới.");
        }
    }

    private void sendResetEmail(String email) {
        ResetDelivery delivery;
        try {
            delivery = transactions.issueCode(email).orElse(null);
        } catch (RuntimeException exception) {
            log.warn("Password reset request could not be processed.");
            return;
        }
        if (delivery == null) {
            return;
        }

        try {
            mailer.sendCode(delivery.email(), delivery.code(), authProperties.getResetCodeTtl().toMinutes());
        } catch (RuntimeException exception) {
            // Lỗi SMTP có thể chứa email hoặc thông tin máy chủ, không đưa vào log/phản hồi.
            log.warn("Password reset email could not be delivered.");
            try {
                transactions.invalidateUndeliveredCode(delivery.userId(), delivery.codeHash());
            } catch (RuntimeException cleanupException) {
                log.warn("Undelivered password reset code could not be cleared; it will expire.");
            }
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthException unavailable() {
        return new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "PASSWORD_RESET_UNAVAILABLE",
                "Chức năng gửi mã đang tạm thời không sẵn sàng. Vui lòng thử lại sau.");
    }
}
