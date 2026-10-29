package vn.tailinh.speakmate.service.google;

/** Danh tính chỉ được tạo từ Google ID token đã xác thực. */
public record GoogleIdentity(
    String subject,
    String email,
    String name) {
}
