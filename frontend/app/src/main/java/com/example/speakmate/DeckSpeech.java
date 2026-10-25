package com.example.speakmate;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import java.util.Locale;

public final class DeckSpeech {

    private final Context appContext;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private TextToSpeech engine;

    private boolean ready;
    private boolean failed;
    private boolean closed;
    private boolean failureMessageShown;

    /*
     * Nếu giọng đọc chưa khởi tạo xong,
     * giữ từ gần nhất để đọc khi sẵn sàng.
     */
    private String pendingWord;

    public DeckSpeech(Context context) {
        appContext = context.getApplicationContext();

        engine = new TextToSpeech(
                appContext,
                status -> mainHandler.post(
                        () -> handleInitialization(status)
                )
        );
    }

    private void handleInitialization(int status) {
        if (closed || engine == null) {
            return;
        }

        if (status != TextToSpeech.SUCCESS) {
            markFailed();
            return;
        }

        int languageResult = engine.setLanguage(Locale.US);

        if (languageResult == TextToSpeech.LANG_MISSING_DATA
                || languageResult
                == TextToSpeech.LANG_NOT_SUPPORTED
                || languageResult < 0) {
            markFailed();
            return;
        }

        engine.setSpeechRate(0.85f);
        engine.setPitch(1.0f);

        ready = true;
        failed = false;

        String word = pendingWord;
        pendingWord = null;

        if (word != null) {
            speak(word);
        }
    }

    public void speak(String word) {
        if (closed || word == null) {
            return;
        }

        String value = word.trim();

        if (value.isEmpty()) {
            return;
        }

        if (failed) {
            showFailureMessage();
            return;
        }

        if (!ready || engine == null) {
            pendingWord = value;
            return;
        }

        pendingWord = null;

        /*
         * Thay âm thanh trước bằng từ hiện tại,
         * không xếp hàng nhiều từ khi người dùng quẹt nhanh.
         */
        int result = engine.speak(
                value,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "speakmate_flashcard_word"
        );

        if (result == TextToSpeech.ERROR) {
            showFailureMessage();
        }
    }

    public void stop() {
        pendingWord = null;

        if (engine != null) {
            engine.stop();
        }
    }

    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        ready = false;
        pendingWord = null;

        mainHandler.removeCallbacksAndMessages(null);

        if (engine != null) {
            engine.stop();
            engine.shutdown();
            engine = null;
        }
    }

    private void markFailed() {
        ready = false;
        failed = true;

        boolean hadPendingWord = pendingWord != null;
        pendingWord = null;

        if (hadPendingWord) {
            showFailureMessage();
        }
    }

    private void showFailureMessage() {
        if (closed || failureMessageShown) {
            return;
        }

        failureMessageShown = true;

        Toast.makeText(
                appContext,
                "Giọng đọc tiếng Anh chưa sẵn sàng. "
                        + "Hãy kiểm tra dịch vụ và dữ liệu giọng đọc "
                        + "trong cài đặt chuyển văn bản thành giọng nói.",
                Toast.LENGTH_LONG
        ).show();
    }
}