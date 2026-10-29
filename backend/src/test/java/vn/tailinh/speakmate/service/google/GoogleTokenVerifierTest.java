package vn.tailinh.speakmate.service.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import vn.tailinh.speakmate.exception.AuthException;

class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "speakmate-test.apps.googleusercontent.com";

    private static final Instant NOW = Instant.parse("2026-10-24T09:00:00Z");

    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static RSAKey trustedKey;

    private static RSAKey unrelatedKey;

    private GoogleTokenVerifier verifier;

    @BeforeAll
    static void keys() throws JOSEException {
        trustedKey = new RSAKeyGenerator(2048).generate();
        unrelatedKey = new RSAKeyGenerator(2048).generate();
    }

    @BeforeEach
    void createVerifier() throws JOSEException {
        verifier = new GoogleTokenVerifier(NimbusJwtDecoder.withPublicKey(trustedKey.toRSAPublicKey()).build(),
                CLIENT_ID, CLOCK);
    }

    @ParameterizedTest
    @ValueSource(strings = { "accounts.google.com", "https://accounts.google.com" })
    void verifiesSignatureAndAcceptsBothOfficialIssuers(String issuer) throws JOSEException {
        String token = signed(
                validClaims().issuer(issuer).claim("email", "  AN@Example.com  ").claim("name", "  Nguyễn An  "),
                trustedKey);

        GoogleIdentity identity = verifier.verify(token);

        assertThat(identity).isEqualTo(new GoogleIdentity("123456789", "an@example.com", "Nguyễn An"));
    }

    @Test
    void acceptsAudienceListContainingExactConfiguredWebClientId() throws JOSEException {
        String token = signed(validClaims().audience(List.of("another-client", CLIENT_ID)), trustedKey);

        assertThat(verifier.verify(token).subject()).isEqualTo("123456789");
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidClaims")
    void rejectsInvalidIdentityClaims(String caseName, Consumer<JWTClaimsSet.Builder> change) throws JOSEException {
        JWTClaimsSet.Builder claims = validClaims();
        change.accept(claims);
        String token = signed(claims, trustedKey);

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(AuthException.class)
                .hasMessage("Thông tin đăng nhập Google không hợp lệ hoặc đã hết hạn.");
    }

    static Stream<Arguments> invalidClaims() {
        return Stream.of(invalid("wrong issuer", b -> b.issuer("https://malicious.example")),
                invalid("missing issuer", b -> b.issuer(null)),
                invalid("issuer with trailing slash", b -> b.issuer("https://accounts.google.com/")),
                invalid("wrong audience", b -> b.audience("other.apps.googleusercontent.com")),
                invalid("audience prefix only", b -> b.audience(CLIENT_ID + ".malicious.example")),
                invalid("missing audience", b -> b.audience((String) null)),
                invalid("expired token", b -> b.expirationTime(Date.from(NOW.minusSeconds(1)))),
                invalid("expiration at current instant", b -> b.expirationTime(Date.from(NOW))),
                invalid("missing expiration", b -> b.expirationTime(null)),
                invalid("future not-before", b -> b.notBeforeTime(Date.from(NOW.plusSeconds(60)))),
                invalid("unverified email", b -> b.claim("email_verified", false)),
                invalid("missing verified flag", b -> b.claim("email_verified", null)),
                invalid("string instead of boolean verified flag", b -> b.claim("email_verified", "true")),
                invalid("missing subject", b -> b.subject(null)), invalid("blank subject", b -> b.subject(" ")),
                invalid("non-ASCII subject", b -> b.subject("nguyễn")),
                invalid("overlong subject", b -> b.subject("1".repeat(256))),
                invalid("missing email", b -> b.claim("email", null)),
                invalid("invalid email", b -> b.claim("email", "not-an-email")),
                invalid("display name in email", b -> b.claim("email", "Someone <an@example.com>")),
                invalid("multiple emails", b -> b.claim("email", "an@example.com,other@example.com")),
                invalid("overlong email", b -> b.claim("email", "a".repeat(250) + "@example.com")));
    }

    @Test
    void rejectsTokenSignedByAnUntrustedKey() throws JOSEException {
        String token = signed(validClaims(), unrelatedKey);

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(AuthException.class)
                .hasMessage("Thông tin đăng nhập Google không hợp lệ hoặc đã hết hạn.");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "not-a-jwt", "e30.e30." })
    void rejectsBlankMalformedAndUnsignedTokens(String token) {
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(AuthException.class);
    }

    @Test
    void returnsConfigurationErrorBeforeTryingToDecode() throws JOSEException {
        GoogleTokenVerifier disabled = new GoogleTokenVerifier(
                NimbusJwtDecoder.withPublicKey(trustedKey.toRSAPublicKey()).build(), " ", CLOCK);

        assertThatThrownBy(() -> disabled.verify("not-a-jwt")).isInstanceOf(AuthException.class)
                .hasMessage("Đăng nhập Google chưa được cấu hình.");
    }

    @Test
    void usesEmailLocalPartWhenNameIsMissing() throws JOSEException {
        String token = signed(validClaims().claim("name", null), trustedKey);

        assertThat(verifier.verify(token).name()).isEqualTo("an");
    }

    @Test
    void limitsDisplayNameWithoutSplittingUnicodeCharacters() throws JOSEException {
        String token = signed(validClaims().claim("name", "😀".repeat(101)), trustedKey);

        assertThat(verifier.verify(token).name()).isEqualTo("😀".repeat(100));
    }

    private static Arguments invalid(String label, Consumer<JWTClaimsSet.Builder> change) {
        return Arguments.of(label, change);
    }

    private JWTClaimsSet.Builder validClaims() {
        return new JWTClaimsSet.Builder().issuer("https://accounts.google.com").audience(CLIENT_ID).subject("123456789")
                .issueTime(Date.from(NOW.minusSeconds(60))).expirationTime(Date.from(NOW.plusSeconds(300)))
                .claim("email", "an@example.com").claim("email_verified", true).claim("name", "An");
    }

    private String signed(JWTClaimsSet.Builder claims, RSAKey key) throws JOSEException {
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        token.sign(new RSASSASigner(key));
        return token.serialize();
    }
}
