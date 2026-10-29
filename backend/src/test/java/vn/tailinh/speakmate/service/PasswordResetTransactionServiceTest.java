package vn.tailinh.speakmate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.tailinh.speakmate.config.AuthProperties;
import vn.tailinh.speakmate.entity.User;
import vn.tailinh.speakmate.repository.UserRepository;
import vn.tailinh.speakmate.service.PasswordResetTransactionService.ResetDelivery;

class PasswordResetTransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-24T03:00:00Z");

    private static final String EMAIL = "reset@example.test";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private UserRepository repository;

    private PasswordResetTransactionService service;

    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        service = new PasswordResetTransactionService(repository, encoder, new AuthProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User();
        user.setId(17L);
        user.setEmail(EMAIL);
        user.setPassword(encoder.encode("OldPassword123!"));
        when(repository.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(repository.findByIdForUpdate(17L)).thenReturn(Optional.of(user));
    }

    @Test
    void issuesSixDigitCodeAndStoresOnlyHashWithExpiry() {
        ResetDelivery delivery = service.issueCode(EMAIL).orElseThrow();

        assertThat(delivery.code()).matches("[0-9]{6}");
        assertThat(user.getPasswordResetCodeHash()).isNotEqualTo(delivery.code());
        assertThat(encoder.matches(delivery.code(), user.getPasswordResetCodeHash())).isTrue();
        assertThat(user.getPasswordResetExpiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(user.getPasswordResetSentAt()).isEqualTo(NOW);
        assertThat(user.getPasswordResetAttempts()).isZero();
    }

    @Test
    void cooldownPreservesPreviouslyIssuedCode() {
        validCode();
        user.setPasswordResetSentAt(NOW.minusSeconds(59));
        String originalHash = user.getPasswordResetCodeHash();

        assertThat(service.issueCode(EMAIL)).isEmpty();
        assertThat(user.getPasswordResetCodeHash()).isEqualTo(originalHash);
    }

    @Test
    void newIssueAtCooldownBoundaryReplacesHashAndClearsAttempts() {
        validCode();
        user.setPasswordResetSentAt(NOW.minusSeconds(60));
        user.setPasswordResetAttempts(4);
        String originalHash = user.getPasswordResetCodeHash();

        ResetDelivery delivery = service.issueCode(EMAIL).orElseThrow();

        assertThat(delivery.codeHash()).isNotEqualTo(originalHash);
        assertThat(encoder.matches(delivery.code(), user.getPasswordResetCodeHash())).isTrue();
        assertThat(user.getPasswordResetAttempts()).isZero();
    }

    @Test
    void unknownAccountHasNoCodeAndCannotReset() {
        assertThat(service.issueCode("unknown@example.test")).isEmpty();
        assertThat(service.verifyAndReset("unknown@example.test", "123456", "NewPassword123!")).isFalse();
    }

    @Test
    void googleOnlyAccountCannotAcquireLocalPasswordThroughReset() {
        user.setPassword(null);
        user.setGoogleSubject("google-subject");
        validCode();

        assertThat(service.issueCode(EMAIL)).isEmpty();
        assertThat(service.verifyAndReset(EMAIL, "123456", "NewPassword123!")).isFalse();
        assertThat(user.getPassword()).isNull();
    }

    @Test
    void disabledAccountCannotIssueOrConsumeCode() {
        validCode();
        user.setActive(false);

        assertThat(service.issueCode(EMAIL)).isEmpty();
        assertThat(service.verifyAndReset(EMAIL, "123456", "NewPassword123!")).isFalse();
    }

    @Test
    void incorrectAttemptReturnsNormallyWithIncrementedCounter() {
        validCode();

        assertThat(service.verifyAndReset(EMAIL, "000000", "NewPassword123!")).isFalse();

        assertThat(user.getPasswordResetAttempts()).isEqualTo(1);
        assertThat(encoder.matches("OldPassword123!", user.getPassword())).isTrue();
    }

    @Test
    void fiveIncorrectAttemptsDisableEvenTheCorrectCode() {
        validCode();
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(service.verifyAndReset(EMAIL, "000000", "NewPassword123!")).isFalse();
        }

        assertThat(service.verifyAndReset(EMAIL, "123456", "NewPassword123!")).isFalse();
        assertThat(user.getPasswordResetAttempts()).isEqualTo(5);
    }

    @Test
    void expiryBoundaryIsAlreadyExpired() {
        validCode();
        user.setPasswordResetExpiresAt(NOW);

        assertThat(service.verifyAndReset(EMAIL, "123456", "NewPassword123!")).isFalse();
        assertThat(user.getPasswordResetAttempts()).isZero();
    }

    @Test
    void successfulResetConsumesCodeAndRevokesExistingSessions() {
        validCode();
        user.setPasswordResetSentAt(NOW.minusSeconds(30));
        user.setPasswordResetAttempts(2);
        user.setRefreshToken("test-refresh-hash");
        user.setRefreshExpiresAt(NOW.plusSeconds(3600));
        user.setAuthVersion(4L);

        assertThat(service.verifyAndReset(EMAIL, "123456", "NewPassword123!")).isTrue();

        assertThat(encoder.matches("NewPassword123!", user.getPassword())).isTrue();
        assertThat(user.getPasswordResetCodeHash()).isNull();
        assertThat(user.getPasswordResetExpiresAt()).isNull();
        assertThat(user.getPasswordResetAttempts()).isZero();
        assertThat(user.getPasswordResetSentAt()).isEqualTo(NOW.minusSeconds(30));
        assertThat(user.getRefreshToken()).isNull();
        assertThat(user.getRefreshExpiresAt()).isNull();
        assertThat(user.getAuthVersion()).isEqualTo(5L);
        assertThat(service.verifyAndReset(EMAIL, "123456", "AnotherPassword123!")).isFalse();
    }

    @Test
    void failedEmailClearsItsCodeButPreservesCooldown() {
        validCode();
        user.setPasswordResetSentAt(NOW);
        service.invalidateUndeliveredCode(user.getId(), user.getPasswordResetCodeHash());

        assertThat(user.getPasswordResetCodeHash()).isNull();
        assertThat(user.getPasswordResetExpiresAt()).isNull();
        assertThat(user.getPasswordResetSentAt()).isEqualTo(NOW);
        assertThat(service.issueCode(EMAIL)).isEmpty();
    }

    @Test
    void lateEmailFailureCannotClearNewerCode() {
        validCode();
        String newerHash = user.getPasswordResetCodeHash();

        service.invalidateUndeliveredCode(user.getId(), "hash-from-older-request");

        assertThat(user.getPasswordResetCodeHash()).isEqualTo(newerHash);
    }

    private void validCode() {
        user.setPasswordResetCodeHash(encoder.encode("123456"));
        user.setPasswordResetExpiresAt(NOW.plusSeconds(600));
    }
}
