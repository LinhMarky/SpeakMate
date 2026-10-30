package com.example.speakmate.api;

import com.example.speakmate.auth.AuthApi;
import com.example.speakmate.auth.SessionManager;

import org.json.JSONObject;

/** API chủ đề quản trị; quyền ADMIN luôn được backend kiểm tra lại. */
public final class AdminApi {

    private final SessionManager sessions;

    public AdminApi(SessionManager sessions) {
        this.sessions = sessions;
    }

    public JSONObject statistics() throws Exception {
        return sessions.request("GET", "admin/statistics", null, 15_000).getJSONObject("data");
    }

    public JSONObject users(int page, String search, String active) throws Exception {
        String query = java.net.URLEncoder.encode(search, "UTF-8");
        return sessions.request("GET", "admin/users?page=" + page + "&size=20&search=" + query
                + (active.isEmpty() ? "" : "&active=" + active), null, 15_000).getJSONObject("data");
    }

    public JSONObject userActive(long id, boolean active) throws Exception {
        return sessions.request("PATCH", "admin/users/" + id + "/active",
                new JSONObject().put("active", active), 15_000).getJSONObject("data");
    }

    public JSONObject decks(int page, boolean admin) throws Exception {
        return sessions.request("GET", (admin ? "admin/" : "") + "system-decks?page=" + page + "&size=20",
                null, 15_000).getJSONObject("data");
    }

    public JSONObject saveDeck(long id, JSONObject body) throws Exception {
        JSONObject data = sessions.request(id > 0 ? "PUT" : "POST",
                "admin/system-decks" + (id > 0 ? "/" + id : ""), body, 15_000).getJSONObject("data");
        requireTopicId(data, id);
        return data;
    }

    public JSONObject deckActive(long id, boolean active) throws Exception {
        return sessions.request("PATCH", "admin/system-decks/" + id + "/active",
                new JSONObject().put("active", active), 15_000).getJSONObject("data");
    }

    public org.json.JSONArray saveSystemDeck(long id) throws Exception {
        return sessions.request("POST", "system-decks/" + id + "/save", null, 15_000).getJSONArray("data");
    }

    public void changePassword(String current, String next) throws Exception {
        sessions.request("PUT", "admin/password", new JSONObject()
                .put("currentPassword", current).put("newPassword", next), 15_000);
    }

    public JSONObject topics(int page, int size) throws Exception {
        JSONObject data = sessions.request("GET", "admin/topics?page=" + page + "&size=" + size, null, 15_000)
                .getJSONObject("data");
        data.getJSONArray("result");
        data.getJSONObject("meta").getLong("total");
        data.getJSONObject("meta").getInt("pages");
        return data;
    }

    public JSONObject saveTopic(long id, JSONObject body) throws Exception {
        String path = id > 0 ? "admin/topics/" + id : "admin/topics";
        JSONObject topic = sessions.request(id > 0 ? "PUT" : "POST", path, body, 15_000)
                .getJSONObject("data");
        requireTopicId(topic, id);
        return topic;
    }

    public JSONObject setActive(long id, boolean active) throws Exception {
        JSONObject topic = sessions.request("PATCH", "admin/topics/" + id + "/active",
                new JSONObject().put("active", active), 15_000).getJSONObject("data");
        requireTopicId(topic, id);
        if (!topic.has("active") || topic.getBoolean("active") != active) throw invalidResponse();
        return topic;
    }

    private void requireTopicId(JSONObject topic, long expectedId) throws AuthApi.ApiException {
        long id = topic.optLong("id", 0);
        if (id <= 0 || expectedId > 0 && id != expectedId) throw invalidResponse();
    }

    private AuthApi.ApiException invalidResponse() {
        return new AuthApi.ApiException(0, "INVALID_RESPONSE",
                "Chưa xác nhận được thay đổi. Hãy tải lại danh sách chủ đề để kiểm tra.");
    }
}
