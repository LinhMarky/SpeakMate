package com.example.speakmate.auth;

import android.content.Context;
import com.example.speakmate.api.ApiClient;
import com.example.speakmate.auth.AuthApi.ApiException;
import org.json.*;

/** Dùng chung phiên và khóa refresh; lời gọi AI không giữ khóa này. */
public final class SessionManager {

    private static SessionManager instance;

    private final Object lock = new Object();

    private final SessionStore store;

    private final ApiClient http = new ApiClient();

    private final AuthApi auth = new AuthApi();

    private SessionManager(Context context) {
        store = new SessionStore(context.getApplicationContext());
    }

    public static synchronized SessionManager get(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    public AuthSession load() {
        synchronized (lock) {
            return store.load();
        }
    }

    public void save(AuthSession session) throws Exception {
        synchronized (lock) {
            store.save(session);
        }
    }

    public void clear() {
        synchronized (lock) {
            store.clear();
        }
    }

    public JSONObject request(String method, String path, JSONObject body, int timeoutMs) throws ApiException {
        AuthSession original = load();
        if (original == null) {
            throw expired();
        }
        try {
            return http.request(method, path, body, original.accessToken, timeoutMs);
        } catch (ApiException first) {
            if (first.status != 401) {
                throw first;
            }
        }
        AuthSession rotated;
        // Các yêu cầu cùng lỗi 401 dùng chung một lần refresh, tránh tiêu thụ token hai lần.
        synchronized (lock) {
            rotated = store.load();
            if (rotated == null) {
                throw expired();
            }
            if (rotated.user.optLong("id") != original.user.optLong("id")) {
                throw expired();
            }
            if (rotated.accessToken.equals(original.accessToken)) {
                try {
                    rotated = auth.refresh(rotated.refreshToken);
                    store.save(rotated);
                } catch (ApiException error) {
                    if (error.status == 401 || error.status == 403) {
                        store.clear();
                    }
                    throw error;
                } catch (Exception error) {
                    // Server có thể đã đổi token; xóa phiên nếu không lưu được token mới.
                    store.clear();
                    throw expired();
                }
            }
        }
        try {
            return http.request(method, path, body, rotated.accessToken, timeoutMs);
        } catch (ApiException second) {
            if (second.status == 401) {
                synchronized (lock) {
                    AuthSession current = store.load();
                    if (current != null && current.accessToken.equals(rotated.accessToken)) {
                        store.clear();
                    }
                }
            }
            throw second;
        }
    }

    public void restore() throws ApiException {
        try {
            JSONObject user = request("GET", "auth/me", null, 15_000).getJSONObject("data");
            updateUser(user);
        } catch (JSONException error) {
            throw new ApiException(0, "INVALID_RESPONSE", "Không đọc được hồ sơ.");
        }
    }

    public void updateUser(JSONObject user) throws ApiException {
        synchronized (lock) {
            AuthSession current = store.load();
            if (current == null) {
                throw expired();
            }
            if (current.user.optLong("id") != user.optLong("id")) {
                throw expired();
            }
            try {
                store.save(new AuthSession(current.accessToken, current.refreshToken, user));
            } catch (Exception error) {
                throw new ApiException(0, "LOCAL_STORAGE_ERROR", "Không lưu được hồ sơ.");
            }
        }
    }

    private ApiException expired() {
        return new ApiException(401, "UNAUTHENTICATED", "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.");
    }
}
