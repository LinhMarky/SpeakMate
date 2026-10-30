package com.example.speakmate.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Bundle;
import android.util.Base64;

import androidx.credentials.CustomCredential;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException;
import androidx.credentials.exceptions.NoCredentialException;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/** Chỉ kiểm dữ liệu SDK cục bộ, không đăng nhập Google hoặc tạo tài khoản. */
@RunWith(AndroidJUnit4.class)
public class GoogleSignInTest {

    @Test
    public void readsOnlyGoogleIdTokenFromCredential() throws Exception {
        String token = fixtureIdToken();
        GoogleIdTokenCredential google = new GoogleIdTokenCredential.Builder()
                .setId("learner@example.invalid").setIdToken(token).build();
        CustomCredential credential = new CustomCredential(google.getType(), google.getData());

        assertEquals(token, GoogleSignIn.readIdToken(credential));
    }

    @Test
    public void rejectsOtherCredentialTypes() {
        assertInvalid(new CustomCredential("other-provider", new Bundle()));
    }

    @Test
    public void rejectsIncompleteGoogleCredential() {
        assertInvalid(new CustomCredential(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, new Bundle()));
    }

    @Test
    public void cancellationAndMissingAccountHaveActionableMessages() {
        assertTrue(GoogleSignIn.errorMessage(new GetCredentialCancellationException("fixture")).contains("hủy"));
        assertTrue(GoogleSignIn.errorMessage(new NoCredentialException("fixture")).contains("thêm tài khoản"));
        assertTrue(GoogleSignIn.errorMessage(new GetCredentialProviderConfigurationException("fixture"))
                .contains("Google Play services"));
    }

    private void assertInvalid(CustomCredential credential) {
        try {
            GoogleSignIn.readIdToken(credential);
            fail("Invalid credential must not be sent to the backend");
        } catch (AuthApi.ApiException error) {
            assertEquals("INVALID_GOOGLE_CREDENTIAL", error.code);
        }
    }

    private String fixtureIdToken() throws Exception {
        // Đúng định dạng JWT cho SDK; chữ ký giả, không gửi đến Google/backend.
        JSONObject header = new JSONObject().put("alg", "RS256").put("typ", "JWT");
        JSONObject payload = new JSONObject().put("sub", "fixture-user").put("email", "learner@example.invalid")
                .put("email_verified", true).put("iss", "https://accounts.google.com").put("aud", "fixture-client")
                .put("iat", System.currentTimeMillis() / 1000).put("exp", System.currentTimeMillis() / 1000 + 300);
        int flags = Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING;
        return Base64.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8), flags) + "."
                + Base64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8), flags) + "."
                + Base64.encodeToString("fixture-signature".getBytes(StandardCharsets.UTF_8), flags);
    }
}
