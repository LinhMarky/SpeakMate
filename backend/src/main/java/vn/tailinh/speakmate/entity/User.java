package vn.tailinh.speakmate.entity;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.tailinh.speakmate.util.constant.EnglishLevel;
import vn.tailinh.speakmate.util.constant.Role;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // Chỉ lưu mật khẩu đã băm; service chịu trách nhiệm băm trước khi lưu.
    @Column(length = 200)
    private String password;

    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR) // Khớp kiểu VARCHAR + CHECK trong speakmate.sql.
    @Column(nullable = false, length = 2)
    private EnglishLevel level = EnglishLevel.A1;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    // Chỉ lưu SHA-256; token gốc chỉ được trả khi cấp phiên.
    private String refreshToken;

    @Column(name = "refresh_expires_at", columnDefinition = "DATETIME(6)")
    private Instant refreshExpiresAt;

    // Tăng giá trị này để vô hiệu access token đã cấp trước đó.
    @Column(name = "auth_version", nullable = false)
    private long authVersion;

    @Column(name = "password_reset_code_hash", length = 200)
    private String passwordResetCodeHash;

    @Column(name = "password_reset_expires_at", columnDefinition = "DATETIME(6)")
    private Instant passwordResetExpiresAt;

    @Column(name = "password_reset_sent_at", columnDefinition = "DATETIME(6)")
    private Instant passwordResetSentAt;

    @Column(name = "password_reset_attempts", nullable = false)
    private int passwordResetAttempts;
}
