package com.example.speakmate;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class HoldToSpeakController {

    public interface Listener {
        void onStateChanged(boolean busy, String message);

        void onResult(String text);

        void onError(String message);
    }

    private final Context context;
    private final Listener listener;
    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;

    private boolean ready;
    private boolean processing;
    private boolean stopRequested;
    private boolean closed;

    private final Runnable resultTimeout = () ->
            fail("Nhận dạng mất quá nhiều thời gian. Bạn hãy thử lại.");

    public HoldToSpeakController(
            @NonNull Context context,
            @NonNull Listener listener
    ) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        if (closed || recognizer != null) {
            return;
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {
            listener.onError("SpeakMate chưa được cấp quyền micro.");
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onError(
                    "Thiết bị chưa có dịch vụ nhận dạng giọng nói."
            );
            return;
        }

        ready = false;
        processing = false;
        stopRequested = false;

        try {
            final SpeechRecognizer engine =
                    SpeechRecognizer.createSpeechRecognizer(context);

            recognizer = engine;

            engine.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    if (!isCurrent(engine)) {
                        return;
                    }

                    ready = true;

                    if (stopRequested) {
                        stop();
                    } else {
                        listener.onStateChanged(
                                true,
                                "Đang nghe… Nói tiếng Anh và thả tay khi xong."
                        );
                    }
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                    if (!isCurrent(engine)) {
                        return;
                    }

                    processing = true;

                    listener.onStateChanged(
                            true,
                            "Đã dừng nghe. Đang nhận dạng…"
                    );

                    waitForResult();
                }

                @Override
                public void onError(int error) {
                    if (isCurrent(engine)) {
                        fail(errorMessage(error));
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    if (!isCurrent(engine)) {
                        return;
                    }

                    ArrayList<String> candidates =
                            results == null
                                    ? null
                                    : results.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                            );

                    String text = "";

                    if (candidates != null) {
                        for (String candidate : candidates) {
                            if (candidate != null
                                    && !candidate.trim().isEmpty()) {
                                text = candidate.trim();
                                break;
                            }
                        }
                    }

                    release();

                    if (text.isEmpty()) {
                        listener.onError(
                                "Chưa nhận được lời nói. Bạn hãy thử lại."
                        );
                    } else {
                        listener.onResult(text);
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });

            Intent intent = new Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "en-US"
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    1
            );

            listener.onStateChanged(
                    true,
                    "Đang mở micro…"
            );

            engine.startListening(intent);

        } catch (RuntimeException exception) {
            fail("Không mở được micro. Bạn hãy thử lại.");
        }
    }

    public void stop() {
        if (closed || recognizer == null || processing) {
            return;
        }

        stopRequested = true;

        // Nếu thả tay trước khi dịch vụ sẵn sàng,
        // đợi onReadyForSpeech rồi mới yêu cầu dừng.
        if (!ready) {
            listener.onStateChanged(
                    true,
                    "Đang kết thúc nhận giọng nói…"
            );

            waitForResult();
            return;
        }

        processing = true;

        listener.onStateChanged(
                true,
                "Đã dừng nghe. Đang nhận dạng…"
        );

        waitForResult();

        try {
            recognizer.stopListening();
        } catch (RuntimeException exception) {
            fail("Không xử lý được giọng nói. Bạn hãy thử lại.");
        }
    }

    public void cancel() {
        release();
    }

    public void close() {
        closed = true;
        release();
    }

    private boolean isCurrent(SpeechRecognizer engine) {
        return !closed && recognizer == engine;
    }

    private void waitForResult() {
        handler.removeCallbacks(resultTimeout);
        handler.postDelayed(resultTimeout, 15000);
    }

    private void fail(String message) {
        release();

        if (!closed) {
            listener.onError(message);
        }
    }

    private void release() {
        handler.removeCallbacks(resultTimeout);

        SpeechRecognizer oldRecognizer = recognizer;
        recognizer = null;

        ready = false;
        processing = false;
        stopRequested = false;

        if (oldRecognizer != null) {
            try {
                oldRecognizer.cancel();
            } catch (RuntimeException ignored) {
            }

            try {
                oldRecognizer.destroy();
            } catch (RuntimeException ignored) {
            }
        }

        if (!closed) {
            listener.onStateChanged(
                    false,
                    "Giữ micro để nói tiếng Anh, thả tay để kết thúc."
            );
        }
    }

    private String errorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Không thu được âm thanh từ micro.";

            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Bạn hãy cấp quyền micro cho SpeakMate.";

            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Lỗi kết nối khi nhận dạng. Bạn hãy kiểm tra mạng.";

            case SpeechRecognizer.ERROR_NO_MATCH:
                return "Chưa nhận rõ câu nói. Bạn hãy giữ micro và thử lại.";

            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "Chưa nghe thấy lời nói. Bạn hãy thử lại.";

            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Dịch vụ nhận dạng đang bận. Bạn hãy thử lại.";

            default:
                return "Chưa nhận dạng được giọng nói. Bạn hãy thử lại.";
        }
    }
}