package com.example.speakmate.api;

import android.content.Context;
import android.util.AtomicFile;
import com.example.speakmate.auth.SessionManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Lưu UUID và nội dung trước khi gửi mạng, tách theo người dùng và không sao lưu. */
public final class PendingRequests {

    private static final Object LOCK = new Object();

    private final AtomicFile file;

    public PendingRequests(Context context) {
        var session = SessionManager.get(context).load();
        long userId = session == null ? 0 : session.user.optLong("id");
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), "pending-user-" + userId + ".json"));
    }

    private JSONObject read() {
        try {
            return new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    public JSONObject get(String key) {
        synchronized (LOCK) {
            return read().optJSONObject(key);
        }
    }

    public void put(String key, JSONObject request) {
        synchronized (LOCK) {
            try {
                JSONObject data = read();
                data.put(key, request);
                write(data);
            } catch (JSONException error) {
                throw new IllegalStateException("Cannot store pending request");
            }
        }
    }

    public void clear(String key) {
        synchronized (LOCK) {
            JSONObject data = read();
            data.remove(key);
            write(data);
        }
    }

    private void write(JSONObject data) {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(data.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (IOException error) {
            if (output != null) {
                file.failWrite(output);
            }
            throw new IllegalStateException("Cannot store pending request");
        }
    }
}
