package vn.tailinh.speakmate.config;

import static org.assertj.core.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import vn.tailinh.speakmate.entity.User;
import vn.tailinh.speakmate.exception.AuthException;
import vn.tailinh.speakmate.service.PasswordPolicy;
import vn.tailinh.speakmate.service.TokenService;

class TokenSecurityTest {

    final AuthProperties properties = new AuthProperties();

    final SecurityConfiguration config = new SecurityConfiguration();

    final Clock clock = Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC);

    final JwtEncoder encoder;

    final JwtDecoder decoder;

    final TokenService tokens;

    TokenSecurityTest() {
        properties.setJwtSecret("VEVTVF9PTkxZX05PVF9BX1JFQUxfU0VDUkVUXzEyMzQ1Njc4OTA=");
        SecretKey key = config.jwtKey(properties);
        encoder = config.jwtEncoder(key);
        decoder = config.jwtDecoder(key, properties, clock);
        tokens = new TokenService(encoder, properties, clock);
    }

    @Test
    void accessTokenHasExpectedLifetimeAndSessionVersion() {
        User user = new User();
        user.setId(7L);
        user.setAuthVersion(3L);
        Jwt jwt = decoder.decode(tokens.createAccessToken(user));
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(((Number) jwt.getClaim("auth_version")).longValue()).isEqualTo(3L);
        assertThat(jwt.getExpiresAt()).isEqualTo(clock.instant().plusSeconds(900));
        assertThat(jwt.getClaims()).doesNotContainKeys("password", "email", "refresh_token");
    }

    @Test
    void rejectsWrongIssuerAudienceTypeMissingExpiryExpiredAndBadSignature() {
        assertInvalid(claims().issuer("other").build());
        assertInvalid(claims().audience(List.of("other")).build());
        assertInvalid(claims().claim("token_type", "refresh").build());
        assertInvalid(JwtClaimsSet.builder().issuer(properties.getIssuer()).subject("1").issuedAt(clock.instant())
                .audience(List.of(properties.getAudience())).claim("token_type", "access").claim("auth_version", 0)
                .build());
        assertInvalid(claims().issuedAt(clock.instant().minusSeconds(600)).expiresAt(clock.instant().minusSeconds(60))
                .build());
        properties.setJwtSecret("T1RIRVJfVEVTVF9PTkxZX05PVF9BX1JFQUxfU0VDUkVUXzEyMzQ1Ng==");
        JwtEncoder wrong = config.jwtEncoder(config.jwtKey(properties));
        String forged = wrong
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims().build()))
                .getTokenValue();
        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void refreshIsRandomHashedAndRejectsMalformedInput() {
        String a = tokens.newRefreshToken(7), b = tokens.newRefreshToken(7);
        assertThat(a).isNotEqualTo(b);
        assertThat(tokens.refreshUserId(a)).isEqualTo(7);
        assertThat(tokens.matchesToken(a, tokens.hashToken(a))).isTrue();
        assertThat(tokens.matchesToken(b, tokens.hashToken(a))).isFalse();
        assertThatThrownBy(() -> tokens.refreshUserId("9999999999999999999." + "a".repeat(43)))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> tokens.refreshUserId("7.short")).isInstanceOf(AuthException.class);
    }

    @Test
    void bcryptPolicyCountsUtf8BytesAndDoesNotTrimPassword() {
        PasswordPolicy policy = new PasswordPolicy();
        assertThatThrownBy(() -> policy.validateNewPassword("é".repeat(40))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> policy.validateNewPassword(" ".repeat(10))).isInstanceOf(AuthException.class);
        assertThatCode(() -> policy.validateNewPassword(" a valid password ")).doesNotThrowAnyException();
    }

    private JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder().issuer(properties.getIssuer()).audience(List.of(properties.getAudience()))
                .subject("1").issuedAt(clock.instant()).expiresAt(clock.instant().plusSeconds(900))
                .claim("token_type", "access").claim("auth_version", 0);
    }

    private void assertInvalid(JwtClaimsSet claims) {
        String value = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        assertThatThrownBy(() -> decoder.decode(value)).isInstanceOf(JwtException.class);
    }
}
