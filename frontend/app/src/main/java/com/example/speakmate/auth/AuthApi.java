package com.example.speakmate.auth;

import com.example.speakmate.api.ApiClient;
import org.json.*;

/** API tài khoản công khai; API cần đăng nhập đi qua SessionManager. */
public final class AuthApi {

    private final ApiClient http = new ApiClient();

    public AuthSession login(String email, String password) throws ApiException {
        try {
            return tokens("login", new JSONObject().put("email", email).put("password", password));
        } catch (JSONException error) {
            throw invalid();
        }
    }

    public AuthSession google(String idToken) throws ApiException {
        try {
            return tokens("google", new JSONObject().put("idToken", idToken));
        } catch (JSONException error) {
            throw invalid();
        }
    }

    public AuthSession refresh(String refreshToken) throws ApiException {
        try {
            return tokens("refresh", new JSONObject().put("refreshToken", refreshToken));
        } catch (JSONException error) {
            throw invalid();
        }
    }

    public void register(String name, String email, String password) throws ApiException {
        try {
            request("register", new JSONObject().put("name", name).put("email", email).put("password", password));
        } catch (JSONException error) {
            throw invalid();
        }
    }

    public void forgot(String email) throws ApiException {
        try {
            request("password/forgot", new JSONObject().put("email", email));
        } catch (JSONException error) {
            throw invalid();
        }
    }

    public void reset(String email, String code, String password) throws ApiException {
        try {
            request("password/reset",
                    new JSONObject().put("email", email).put("code", code).put("newPassword", password));
        } catch (JSONException error) {
            throw invalid();
        }
    }

    private JSONObject request(String endpoint, JSONObject body) throws ApiException {
        return http.request("POST", "auth/" + endpoint, body, null, 15_000);
    }

    private AuthSession tokens(String endpoint, JSONObject body) throws ApiException {
        try {
            return AuthSession.fromJson(request(endpoint, body).getJSONObject("data"));
        } catch (JSONException error) {
            throw invalid();
        }
    }

    private ApiException invalid() {
        return new ApiException(0, "INVALID_RESPONSE", "Chưa thể đọc phản hồi. Vui lòng thử lại.");
    }

    public static final class ApiException extends Exception {

        public final int status;

        public final String code;

        public ApiException(int status, String message) {
            this(status, "REQUEST_FAILED", message);
        }

        public ApiException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
