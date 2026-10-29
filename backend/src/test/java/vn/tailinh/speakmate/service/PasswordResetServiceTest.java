package vn.tailinh.speakmate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import vn.tailinh.speakmate.config.AuthProperties;
import vn.tailinh.speakmate.exception.AuthException;
import vn.tailinh.speakmate.service.PasswordResetTransactionService.ResetDelivery;
import vn.tailinh.speakmate.service.mail.PasswordResetMailer;

class PasswordResetServiceTest {

    private PasswordResetTransactionService transactions;

    private PasswordResetMailer mailer;

    private TaskExecutor executor;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        transactions = mock(PasswordResetTransactionService.class);
        mailer = mock(PasswordResetMailer.class);
        executor = mock(TaskExecutor.class);
        service = new PasswordResetService(transactions, mailer, new PasswordPolicy(), new AuthProperties(), executor);
        when(mailer.isConfigured()).thenReturn(true);
    }

    @Test
    void missingGlobalSmtpConfigurationFailsBeforeAccountLookup() {
        when(mailer.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.requestReset("someone@example.test")).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(transactions, executor);
    }

    @Test
    void requestReturnsBeforeAccountLookupAndMailSending() {
        service.requestReset("  LEARNER@example.test  ");

        verifyNoInteractions(transactions);
        Runnable task = queuedTask();
        task.run();
        verify(transactions).issueCode("learner@example.test");
        verify(mailer, never()).sendCode(any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void saturatedMailQueueReturnsGenericUnavailable() {
        doThrow(new RejectedExecutionException()).when(executor).execute(any(Runnable.class));

        assertThatThrownBy(() -> service.requestReset("learner@example.test")).isInstanceOfSatisfying(
                AuthException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("PASSWORD_RESET_UNAVAILABLE"));
        verifyNoInteractions(transactions);
    }

    @Test
    void committedDeliveryIsPassedToMailerWithoutAdditionalDatabaseWork() {
        ResetDelivery delivery = new ResetDelivery(9L, "learner@example.test", "123456", "stored-hash");
        when(transactions.issueCode(delivery.email())).thenReturn(Optional.of(delivery));

        service.requestReset(delivery.email());
        queuedTask().run();

        verify(mailer).sendCode(delivery.email(), delivery.code(), 10);
        verify(transactions, never()).invalidateUndeliveredCode(any(), any());
    }

    @Test
    void failedEmailInvalidatesOnlyExpectedCodeWithoutEscapingAsyncTask() {
        ResetDelivery delivery = new ResetDelivery(9L, "learner@example.test", "123456", "stored-hash");
        when(transactions.issueCode(delivery.email())).thenReturn(Optional.of(delivery));
        doThrow(new IllegalStateException("SMTP failure")).when(mailer).sendCode(delivery.email(), delivery.code(), 10);

        service.requestReset(delivery.email());
        queuedTask().run();

        verify(transactions).invalidateUndeliveredCode(9L, "stored-hash");
    }

    @Test
    void malformedPasswordNeverConsumesCodeOrTouchesDatabase() {
        assertThatThrownBy(() -> service.resetPassword("learner@example.test", "123456", "short"))
                .isInstanceOfSatisfying(AuthException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_PASSWORD"));
        verifyNoInteractions(transactions);
    }

    @Test
    void invalidResetOutcomeBecomesHttpErrorAfterTransactionalCallReturns() {
        when(transactions.verifyAndReset("learner@example.test", "000000", "NewPassword123!")).thenReturn(false);

        assertThatThrownBy(() -> service.resetPassword(" LEARNER@example.test ", "000000", "NewPassword123!"))
                .isInstanceOfSatisfying(AuthException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("INVALID_RESET_CODE");
                });
    }

    @Test
    void successfulResetReturnsWithoutAutomaticLogin() {
        when(transactions.verifyAndReset("learner@example.test", "123456", "NewPassword123!")).thenReturn(true);

        service.resetPassword("learner@example.test", "123456", "NewPassword123!");

        verifyNoInteractions(executor, mailer);
    }

    private Runnable queuedTask() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());
        return task.getValue();
    }
}
