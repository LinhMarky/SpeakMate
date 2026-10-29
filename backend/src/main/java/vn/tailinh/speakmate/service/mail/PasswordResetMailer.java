package vn.tailinh.speakmate.service.mail;

public interface PasswordResetMailer {

    boolean isConfigured();

    void sendCode(String email, String code, long expiresMinutes);
}
