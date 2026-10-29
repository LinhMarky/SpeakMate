package vn.tailinh.speakmate.service.google;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import vn.tailinh.speakmate.config.AuthProperties;
import vn.tailinh.speakmate.exception.AuthException;

/** Kiểm tra chữ ký Google trước khi dùng danh tính; không ghi log ID token. */
@Component
public class GoogleTokenVerifier implements GoogleIdentityVerifier {

    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private static final Pattern SUBJECT_PATTERN = Pattern.compile("[\\x21-\\x7E]{1,255}");

    private final NimbusJwtDecoder decoder;

    private final String clientId;

    private final Clock clock;

    @Autowired
    public GoogleTokenVerifier(AuthProperties properties, RestTemplateBuilder restTemplateBuilder, Clock clock) {
        this(NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI).jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(
                restTemplateBuilder.connectTimeout(Duration.ofSeconds(3)).readTimeout(Duration.ofSeconds(5)).build())
                .build(), properties.getGoogleClientId(), clock);
        // Dùng URL khóa công khai cố định để không cần gọi discovery khi khởi động.
    }

    // Test dùng khóa RSA cục bộ để kiểm tra chữ ký mà không gọi Google.
    GoogleTokenVerifier(NimbusJwtDecoder decoder, String clientId, Clock clock) {
        this.decoder = decoder;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clock = clock;
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(timestamps);
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (clientId.isBlank()) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_NOT_CONFIGURED",
                    "Đăng nhập Google chưa được cấu hình.");
        }
        if (idToken == null || idToken.isBlank() || idToken.length() > 10000) {
            throw invalidToken();
        }

        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException exception) {
            if (hasNetworkFailure(exception)) {
                throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_UNAVAILABLE",
                        "Chưa thể xác minh với Google. Vui lòng thử lại sau.");
            }
            throw invalidToken();
        }

        // Bộ kiểm tra mặc định cho phép thiếu exp, nhưng Google ID token phải có hạn dùng.
        String issuer = jwt.getClaimAsString("iss");
        if (jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(clock.instant()) || issuer == null
                || !GOOGLE_ISSUERS.contains(issuer) || jwt.getAudience() == null
                || !jwt.getAudience().contains(clientId)
                || !Boolean.TRUE.equals(jwt.getClaims().get("email_verified"))) {
            throw invalidToken();
        }

        String subject = jwt.getSubject();
        if (subject == null || !SUBJECT_PATTERN.matcher(subject).matches()) {
            throw invalidToken();
        }
        String email = verifiedEmail(jwt.getClaims().get("email"));
        String name = displayName(jwt.getClaims().get("name"), email);
        return new GoogleIdentity(subject, email, name);
    }

    private String verifiedEmail(Object claim) {
        if (!(claim instanceof String value)) {
            throw invalidToken();
        }
        String email = value.trim().toLowerCase(Locale.ROOT);
        if (email.isBlank() || email.length() > 255) {
            throw invalidToken();
        }
        try {
            InternetAddress address = new InternetAddress(email, true);
            address.validate();
            // Chỉ nhận địa chỉ email thuần, không nhận dạng "Tên <email>".
            if (!email.equals(address.getAddress()) || address.getPersonal() != null) {
                throw invalidToken();
            }
        } catch (AddressException exception) {
            throw invalidToken();
        }
        return email;
    }

    private String displayName(Object claim, String email) {
        String name = claim instanceof String value ? value.strip() : "";
        if (name.isBlank()) {
            name = email.substring(0, email.indexOf('@'));
        }
        int end = name.offsetByCodePoints(0, Math.min(100, name.codePointCount(0, name.length())));
        return name.substring(0, end);
    }

    private boolean hasNetworkFailure(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof RestClientException) {
                return true;
            }
        }
        return false;
    }

    private AuthException invalidToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN",
                "Thông tin đăng nhập Google không hợp lệ hoặc đã hết hạn.");
    }
}
