package com.example.speakmate.auth;

import org.json.JSONException;
import org.json.JSONObject;

/** Chỉ lưu token SpeakMate; Google ID token dùng một lần để đổi lấy phiên. */
public final class AuthSession {

    public final String accessToken;

    public final String refreshToken;

    public final JSONObject user;

    public AuthSession(String accessToken, String refreshToken, JSONObject user) throws JSONException {
        if (accessToken.isEmpty() || refreshToken.isEmpty() || user.getLong("id") <= 0) {
            throw new JSONException("Invalid account response");
        }
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.user = user;
    }

    public static AuthSession fromJson(JSONObject data) throws JSONException {
        return new AuthSession(data.getString("access_token"), data.getString("refresh_token"),
                data.getJSONObject("user"));
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("access_token", accessToken).put("refresh_token", refreshToken).put("user", user);
    }

    public String getName() {
        return user.optString("name", "Bạn");
    }

    public String getEmail() {
        return user.optString("email", "");
    }

    public boolean isGoogleLinked() {
        return user.optBoolean("googleLinked");
    }

    public boolean isAdmin() {
        return "ADMIN".equals(user.optString("role"));
    }
}
