package vn.tailinh.speakmate.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import vn.tailinh.speakmate.config.AuthProperties;
import vn.tailinh.speakmate.entity.User;
import vn.tailinh.speakmate.exception.AuthException;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder jwtEncoder;

    private final AuthProperties properties;

    private final Clock clock;

    private final SecureRandom random = new SecureRandom();

    public String newRefreshToken(long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return userId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public long refreshUserId(String raw) {
        if (raw != null && raw.matches("[1-9][0-9]{0,18}\\.[A-Za-z0-9_-]{43}")) {
            try {
                return Long.parseLong(raw.substring(0, raw.indexOf('.')));
            } catch (NumberFormatException ignored) {
                // ID vượt giới hạn cũng được xem là token không hợp lệ.
            }
        }
        throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Phiên đăng nhập không hợp lệ.");
    }

    public String hashToken(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public boolean matchesToken(String raw, String storedHash) {
        return raw != null && storedHash != null && MessageDigest.isEqual(
                hashToken(raw).getBytes(StandardCharsets.US_ASCII), storedHash.getBytes(StandardCharsets.US_ASCII));
    }

    public String createAccessToken(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.getIssuer())
                .audience(List.of(properties.getAudience())).subject(user.getId().toString()).issuedAt(now)
                .expiresAt(now.plus(properties.getAccessTokenTtl())).claim("token_type", "access")
                .claim("auth_version", user.getAuthVersion()).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    public Instant refreshExpiry() {
        return clock.instant().plus(properties.getRefreshTokenTtl());
    }

    public long accessExpiresInSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }
}
