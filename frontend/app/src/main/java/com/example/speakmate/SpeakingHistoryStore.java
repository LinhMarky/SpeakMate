package com.example.speakmate;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SpeakingHistoryStore {

    private static final String PREFS_NAME =
            "speakmate_speaking_history";

    private static final String KEY_SESSIONS = "sessions";

    private final SharedPreferences preferences;

    public SpeakingHistoryStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );
    }

    public List<SpeakingSession> getSessions() {
        List<SpeakingSession> sessions = readSessions();

        // Phiên kết thúc gần nhất đứng đầu.
        sessions.sort((first, second) ->
                Long.compare(
                        second.getFinishedAt(),
                        first.getFinishedAt()
                )
        );

        return sessions;
    }

    public SpeakingSession getSessionById(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return null;
        }

        for (SpeakingSession session : readSessions()) {
            if (session.getId().equals(sessionId)) {
                return session;
            }
        }

        return null;
    }

    public void saveSession(SpeakingSession session) {
        if (session == null) {
            throw new IllegalArgumentException(
                    "Không có phiên luyện để lưu."
            );
        }

        List<SpeakingSession> sessions = readSessions();

        // Một ID chỉ được thêm một lần.
        for (SpeakingSession existing : sessions) {
            if (existing.getId().equals(session.getId())) {
                return;
            }
        }

        sessions.add(session);
        writeSessions(sessions);
    }

    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Mã phiên luyện không hợp lệ."
            );
        }

        List<SpeakingSession> sessions = readSessions();
        boolean removed = false;

        for (int i = sessions.size() - 1; i >= 0; i--) {
            if (sessions.get(i).getId().equals(sessionId)) {
                sessions.remove(i);
                removed = true;
            }
        }

        if (removed) {
            writeSessions(sessions);
        }
    }

    private List<SpeakingSession> readSessions() {
        String saved = preferences.getString(
                KEY_SESSIONS,
                "[]"
        );

        List<SpeakingSession> sessions = new ArrayList<>();

        try {
            JSONArray array = new JSONArray(
                    saved == null ? "[]" : saved
            );

            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);

                JSONArray messageArray =
                        item.getJSONArray("messages");

                JSONArray roleArray =
                        item.getJSONArray("userRoles");

                if (messageArray.length() != roleArray.length()) {
                    throw new JSONException(
                            "Số tin nhắn và vai trò không khớp."
                    );
                }

                ArrayList<String> messages = new ArrayList<>();
                boolean[] roles = new boolean[roleArray.length()];

                for (int j = 0; j < messageArray.length(); j++) {
                    if (messageArray.isNull(j)) {
                        throw new JSONException(
                                "Tin nhắn đã lưu không hợp lệ."
                        );
                    }

                    messages.add(messageArray.getString(j));
                    roles[j] = roleArray.getBoolean(j);
                }

                SpeakingSession session = new SpeakingSession(
                        item.getString("id"),
                        item.getString("level"),
                        item.getString("topic"),
                        item.getLong("finishedAt"),
                        messages,
                        roles
                );

                sessions.add(session);
            }

            return sessions;

        } catch (JSONException | IllegalArgumentException exception) {
            // Không trả danh sách rỗng rồi ghi đè lịch sử bị lỗi.
            throw new IllegalStateException(
                    "Không đọc được lịch sử luyện tập đã lưu.",
                    exception
            );
        }
    }

    private void writeSessions(List<SpeakingSession> sessions) {
        JSONArray array = new JSONArray();

        try {
            for (SpeakingSession session : sessions) {
                JSONObject item = new JSONObject();

                JSONArray messages = new JSONArray();
                JSONArray roles = new JSONArray();

                ArrayList<String> sessionMessages =
                        session.getMessages();

                boolean[] sessionRoles =
                        session.getUserRoles();

                for (int i = 0; i < sessionMessages.size(); i++) {
                    messages.put(sessionMessages.get(i));
                    roles.put(sessionRoles[i]);
                }

                item.put("id", session.getId());
                item.put("level", session.getLevel());
                item.put("topic", session.getTopic());
                item.put("finishedAt", session.getFinishedAt());
                item.put("messages", messages);
                item.put("userRoles", roles);

                array.put(item);
            }

        } catch (JSONException exception) {
            throw new IllegalStateException(
                    "Không chuẩn bị được dữ liệu lịch sử.",
                    exception
            );
        }

        boolean saved = preferences.edit()
                .putString(KEY_SESSIONS, array.toString())
                .commit();

        if (!saved) {
            throw new IllegalStateException(
                    "Không lưu được lịch sử trên thiết bị."
            );
        }
    }
}