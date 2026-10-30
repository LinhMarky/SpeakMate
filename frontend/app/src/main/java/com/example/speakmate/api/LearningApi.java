package com.example.speakmate.api;

import com.example.speakmate.auth.*;
import com.example.speakmate.Flashcard;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    public List<Flashcard> generateVocabulary(String topic, String level, int count) throws Exception {
        JSONObject body = new JSONObject()
                .put("topic", topic)
                .put("level", level)
                .put("count", count);
        JSONObject response = sessions.request("POST", "saved-words/generate", body, 45_000);
        JSONArray items = response.getJSONArray("data");
        List<Flashcard> list = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            list.add(new Flashcard(
                    item.getString("word"),
                    item.getString("meaning"),
                    item.optString("example", "")
            ));
        }
        return list;
    }

    public JSONObject saveGeneratedWords(List<Flashcard> cards) throws Exception {
        JSONArray array = new JSONArray();
        for (Flashcard card : cards) {
            JSONObject item = new JSONObject();
            item.put("word", card.getWord());
            item.put("meaning", card.getMeaning());
            item.put("example", card.getExample() == null ? "" : card.getExample());
            array.put(item);
        }
        JSONObject body = new JSONObject().put("words", array);
        return post("saved-words/from-generated", body, 20_000);
    }

    public JSONObject reviewWord(long id, String status) throws Exception {
        return sessions
                .request("PATCH", "saved-words/" + id + "/review", new JSONObject().put("status", status), 15_000)
                .getJSONObject("data");
    }

    public List<Flashcard> savedCards() throws Exception {
        // Giữ ID thật để cập nhật tiến độ; đọc đủ các trang của Từ của tôi.
        LinkedHashMap<Long, Flashcard> cards = new LinkedHashMap<>();
        int pages = 1;
        for (int page = 1; page <= pages; page++) {
            JSONObject data = get("saved-words?page=" + page + "&size=100");
            if (page == 1) pages = data.getJSONObject("meta").getInt("pages");
            JSONArray rows = data.getJSONArray("result");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                long id = row.getLong("id");
                if (id <= 0) throw new JSONException("Invalid saved word ID");
                cards.put(id, new Flashcard(id, row.getString("word"), row.getString("meaning"),
                        row.isNull("example") ? "" : row.optString("example"), row.getString("status")));
            }
        }
        return new ArrayList<>(cards.values());
    }
}
