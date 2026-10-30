package com.example.speakmate.auth;

import android.app.Activity;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.util.function.Consumer;

/** Chọn tài khoản Google; backend kiểm tra ID token và cấp phiên SpeakMate. */
public final class GoogleSignIn {

    private GoogleSignIn() {
    }

    public static void start(Activity activity, String webClientId, CancellationSignal cancellation,
            Consumer<String> success, Consumer<String> failure) {
        if (webClientId.trim().isEmpty()) {
            failure.accept("Đăng nhập Google chưa được cấu hình trên ứng dụng.");
            return;
        }

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(new GetSignInWithGoogleOption.Builder(webClientId.trim()).build())
                .build();

        try {
            CredentialManager.create(activity).getCredentialAsync(activity, request, cancellation,
                    ContextCompat.getMainExecutor(activity),
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(@NonNull GetCredentialResponse result) {
                            if (cancellation.isCanceled()) return;
                            try {
                                success.accept(readIdToken(result.getCredential()));
                            } catch (AuthApi.ApiException error) {
                                failure.accept(error.getMessage());
                            }
                        }

                        @Override
                        public void onError(@NonNull GetCredentialException error) {
                            if (!cancellation.isCanceled()) failure.accept(errorMessage(error));
                        }
                    });
        } catch (IllegalArgumentException | IllegalStateException error) {
            failure.accept("Không thể mở đăng nhập Google. Hãy kiểm tra cấu hình ứng dụng.");
        }
    }

    static String readIdToken(Credential credential) throws AuthApi.ApiException {
        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            throw invalidCredential();
        }
        try {
            String token = GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken();
            if (token.trim().isEmpty()) throw invalidCredential();
            return token;
        } catch (Exception error) {
            throw invalidCredential();
        }
    }

    static String errorMessage(GetCredentialException error) {
        if (error instanceof GetCredentialCancellationException) {
            return "Bạn đã hủy đăng nhập Google.";
        }
        if (error instanceof NoCredentialException) {
            return "Chưa tìm thấy tài khoản Google. Hãy thêm tài khoản trong Cài đặt máy rồi thử lại.";
        }
        if (error instanceof GetCredentialProviderConfigurationException) {
            return "Thiết bị chưa hỗ trợ đăng nhập Google. Hãy kiểm tra Google Play services.";
        }
        return "Chưa thể đăng nhập Google. Hãy kiểm tra mạng rồi thử lại.";
    }

    private static AuthApi.ApiException invalidCredential() {
        return new AuthApi.ApiException(0, "INVALID_GOOGLE_CREDENTIAL",
                "Không đọc được tài khoản Google. Hãy chọn tài khoản lại.");
    }
}
