package com.example.speakmate.api;

import com.example.speakmate.auth.*;
import org.json.*;

/** Gom URL, JSON và timeout; màn hình quyết định lúc tạo UUID hoặc hỏi lại trạng thái. */
public final class LearningApi {

    private final SessionManager sessions;

    public LearningApi(SessionManager sessions) {
        this.sessions = sessions;
    }

    public JSONObject get(String path) throws AuthApi.ApiException, JSONException {
        return sessions.request("GET", path, null, 15_000).getJSONObject("data");
    }

    public JSONObject post(String path, JSONObject body, int timeout) throws AuthApi.ApiException, JSONException {
        return sessions.request("POST", path, body, timeout).getJSONObject("data");
    }

    public JSONObject create(long topicId, String level, String uuid) throws Exception {
        return post("conversations",
                new JSONObject().put("topicId", topicId).put("level", level).put("createRequestId", uuid), 30_000);
    }

    public JSONObject send(long id, String content, String uuid) throws Exception {
        return post("conversations/" + id + "/messages",
                new JSONObject().put("content", content).put("requestId", uuid), 30_000);
    }

    public JSONObject finish(long id, boolean retry) throws Exception {
        return post("conversations/" + id + (retry ? "/review/retry" : "/finish"), null, 60_000);
    }

    public JSONObject saveWords(JSONArray ids) throws Exception {
        return post("saved-words/from-suggestions", new JSONObject().put("suggestionIds", ids), 15_000);
    }

    public JSONObject reviewWord(long id, String status) throws Exception {
        return sessions
                .request("PATCH", "saved-words/" + id + "/review", new JSONObject().put("status", status), 15_000)
                .getJSONObject("data");
    }
}
