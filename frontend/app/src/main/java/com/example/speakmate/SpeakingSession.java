package com.example.speakmate;

import java.util.ArrayList;
import java.util.List;

public class SpeakingSession {

    private final String id;
    private final String level;
    private final String topic;
    private final long finishedAt;

    private final ArrayList<String> messages;
    private final boolean[] userRoles;

    public SpeakingSession(
            String id,
            String level,
            String topic,
            long finishedAt,
            List<String> messages,
            boolean[] userRoles
    ) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Phiên luyện phải có mã định danh."
            );
        }

        if (level == null || level.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Phiên luyện phải có trình độ bài luyện."
            );
        }

        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Phiên luyện phải có chủ đề."
            );
        }

        if (finishedAt <= 0) {
            throw new IllegalArgumentException(
                    "Thời gian phiên luyện không hợp lệ."
            );
        }

        if (messages == null
                || userRoles == null
                || messages.size() != userRoles.length) {
            throw new IllegalArgumentException(
                    "Dữ liệu hội thoại không hợp lệ."
            );
        }

        ArrayList<String> copiedMessages =
                new ArrayList<>(messages);

        for (String message : copiedMessages) {
            if (message == null) {
                throw new IllegalArgumentException(
                        "Hội thoại có tin nhắn không hợp lệ."
                );
            }
        }

        this.id = id.trim();
        this.level = level.trim();
        this.topic = topic.trim();
        this.finishedAt = finishedAt;
        this.messages = copiedMessages;
        this.userRoles = userRoles.clone();
    }

    public String getId() {
        return id;
    }

    public String getLevel() {
        return level;
    }

    public String getTopic() {
        return topic;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public ArrayList<String> getMessages() {
        return new ArrayList<>(messages);
    }

    public boolean[] getUserRoles() {
        return userRoles.clone();
    }

    public int getUserTurnCount() {
        int count = 0;

        for (int i = 0; i < userRoles.length; i++) {
            if (userRoles[i]
                    && !messages.get(i).trim().isEmpty()) {
                count++;
            }
        }

        return count;
    }
}