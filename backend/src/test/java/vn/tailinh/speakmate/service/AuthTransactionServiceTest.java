package vn.tailinh.speakmate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import vn.tailinh.speakmate.dto.request.auth.LoginRequest;
import vn.tailinh.speakmate.entity.User;
import vn.tailinh.speakmate.exception.AuthException;
import vn.tailinh.speakmate.repository.UserRepository;
import vn.tailinh.speakmate.service.google.GoogleIdentity;

@ExtendWith(MockitoExtension.class)
class AuthTransactionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-24T00:00:00Z");

    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwords;

    @Mock
    private TokenService tokens;

    private AuthTransactionService service;

    @BeforeEach
    void setUp() {
        service = new AuthTransactionService(users, passwords, new PasswordPolicy(), tokens,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void unknownAccountStillChecksAPasswordHashAndReturnsGenericFailure() {
        when(users.findByEmailForUpdate("missing@example.test")).thenReturn(Optional.empty());
        LoginRequest request = login("missing@example.test", "Valid-password");

        assertThatThrownBy(() -> service.login(request)).isInstanceOfSatisfying(AuthException.class,
                error -> assertThat(error.getCode()).isEqualTo("INVALID_CREDENTIALS"));

        verify(passwords).matches(eq("Valid-password"), anyString());
        verifyNoInteractions(tokens);
    }

    @Test
    void disabledAccountIsDisclosedOnlyAfterPasswordIsCorrect() {
        User user = user();
        user.setActive(false);
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(passwords.matches("wrong-password", user.getPassword())).thenReturn(false);
        when(passwords.matches("right-password", user.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> service.login(login(user.getEmail(), "wrong-password"))).isInstanceOfSatisfying(
                AuthException.class, error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> service.login(login(user.getEmail(), "right-password"))).isInstanceOfSatisfying(
                AuthException.class, error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(tokens);
    }

    @Test
    void staleLogoutCannotInvalidateASessionCreatedAfterTheRequestWasAuthenticated() {
        User user = user();
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.logout(new UserService.CurrentIdentity(user.getId(), 3)))
                .isInstanceOf(AuthException.class);

        assertThat(user.getAuthVersion()).isEqualTo(4);
        assertThat(user.getRefreshToken()).isEqualTo("stored-refresh-hash");
    }

    @Test
    void refreshExpiringExactlyNowIsRejectedBeforeIssuingAnyToken() {
        User user = user();
        user.setRefreshExpiresAt(NOW);
        when(tokens.refreshUserId("old-refresh")).thenReturn(user.getId());
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.refresh("old-refresh")).isInstanceOf(AuthException.class);

        verify(tokens, never()).newRefreshToken(user.getId());
        assertThat(user.getRefreshToken()).isEqualTo("stored-refresh-hash");
    }

    @Test
    void googleEmailCollisionNeverLinksAnExistingLocalAccountAutomatically() {
        User user = user();
        when(users.findByGoogleSubjectForUpdate("new-subject")).thenReturn(Optional.empty());
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.loginWithGoogle(new GoogleIdentity("new-subject", user.getEmail(), "Name")))
                .isInstanceOfSatisfying(AuthException.class,
                        error -> assertThat(error.getCode()).isEqualTo("GOOGLE_LINK_REQUIRED"));

        assertThat(user.getGoogleSubject()).isNull();
        verify(users, never()).saveAndFlush(any());
        verifyNoInteractions(tokens);
    }

    @Test
    void blockedGoogleAccountCannotCreateANewSession() {
        User user = user();
        user.setActive(false);
        user.setGoogleSubject("subject");
        when(users.findByGoogleSubjectForUpdate("subject")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.loginWithGoogle(new GoogleIdentity("subject", user.getEmail(), "Name")))
                .isInstanceOfSatisfying(AuthException.class,
                        error -> assertThat(error.getCode()).isEqualTo("ACCOUNT_DISABLED"));
        verifyNoInteractions(tokens);
    }

    @Test
    void explicitLinkRejectsDifferentEmailAndLeavesAccountUnchanged() {
        User user = user();
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.linkGoogle(new UserService.CurrentIdentity(user.getId(), 4),
                new GoogleIdentity("subject", "someone-else@example.test", "Name"))).isInstanceOfSatisfying(
                        AuthException.class, error -> assertThat(error.getCode()).isEqualTo("GOOGLE_EMAIL_MISMATCH"));
        assertThat(user.getGoogleSubject()).isNull();
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void staleGoogleLinkCannotChangeAccountAfterNewLoginOrReset() {
        User user = user();
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.linkGoogle(new UserService.CurrentIdentity(user.getId(), 3),
                new GoogleIdentity("subject", user.getEmail(), "Name"))).isInstanceOf(AuthException.class);
        assertThat(user.getGoogleSubject()).isNull();
        verify(users, never()).saveAndFlush(any());
    }

    private User user() {
        User user = new User();
        user.setId(7L);
        user.setEmail("learner@example.test");
        user.setName("Learner");
        user.setPassword("stored-password-hash");
        user.setAuthVersion(4);
        user.setRefreshToken("stored-refresh-hash");
        user.setRefreshExpiresAt(NOW.plusSeconds(60));
        return user;
    }

    private LoginRequest login(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
