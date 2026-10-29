package vn.tailinh.speakmate.service.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpPasswordResetMailer implements PasswordResetMailer {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    private final String from;

    public SmtpPasswordResetMailer(ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.from:}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
    }

    @Override
    public boolean isConfigured() {
        return !from.isBlank() && mailSenderProvider.getIfAvailable() != null;
    }

    @Override
    public void sendCode(String email, String code, long expiresMinutes) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || from.isBlank()) {
            throw new IllegalStateException("Password reset email is not configured.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("SpeakMate - Mã đặt lại mật khẩu");
        message.setText("Mã đặt lại mật khẩu SpeakMate của bạn là: " + code + "\nMã có hiệu lực trong " + expiresMinutes
                + " phút và chỉ dùng một lần." + "\nKhông chia sẻ mã này với người khác."
                + "\nNếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.");
        sender.send(message);
    }
}
