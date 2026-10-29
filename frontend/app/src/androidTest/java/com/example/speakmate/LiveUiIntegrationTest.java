package com.example.speakmate;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.speakmate.api.LearningApi;
import com.example.speakmate.auth.SessionManager;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Opt-in: tạo tài khoản thử mới và dùng quota Gemini thật, không ghi token/mật khẩu. */
@RunWith(AndroidJUnit4.class)
public class LiveUiIntegrationTest {
    static final String[] SENTENCES = {
            "Hello, I would like a medium latte to take away, please. Could you make it with less sugar?",
            "Thank you. Yesterday I go to another cafe, but their coffee was too sweet for me. What do you recommend?",
            "That sounds good. I will try your suggestion today. How much is the drink, and could I have a receipt, please?"
    };
    private String stage = "prepare";

    @Test
    public void criticalSpeakingPath() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("speakmate.liveVerification")));
        assertEquals("http://10.0.2.2:8081/", BuildConfig.API_BASE_URL);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SessionManager sessions = SessionManager.get(context);
        Assume.assumeTrue("Use a dedicated emulator without a personal session.", sessions.load() == null);
        LearningApi api = new LearningApi(sessions);
        JSONObject evidence = new JSONObject().put("transport", "Android UI -> Retrofit -> Spring Boot -> Gemini/MySQL")
                .put("audioVerified", false);
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            String email = "ui-integration-" + UUID.randomUUID() + "@example.test";
            String password = UUID.randomUUID().toString();
            stage = "register";
            screen.onActivity(MainActivity::openLogin);
            waitFor(screen, a -> fragment(a) instanceof LoginFragment);
            click(screen, R.id.btnOpenRegister);
            waitFor(screen, a -> fragment(a) instanceof RegisterFragment);
            screen.onActivity(a -> {
                input(a, R.id.etRegisterName, "UI Integration Learner");
                input(a, R.id.etRegisterEmail, email);
                input(a, R.id.etRegisterPassword, password);
                input(a, R.id.etRegisterConfirmPassword, password);
            });
            click(screen, R.id.btnRegister);
            waitFor(screen, a -> fragment(a) instanceof LoginFragment);
            stage = "login";
            screen.onActivity(a -> {
                input(a, R.id.etLoginUsername, email);
                input(a, R.id.etLoginPassword, password);
            });
            click(screen, R.id.btnLogin);
            waitFor(screen, a -> fragment(a) instanceof SpeakingFragment && a.findViewById(R.id.rgTopic) != null
                    && ((RadioGroup) a.findViewById(R.id.rgTopic)).getChildCount() > 0);
            assertNotNull(sessions.load());
            long userId = sessions.load().user.getLong("id");
            evidence.put("userId", userId);
            stage = "topic_and_create";
            screen.onActivity(a -> {
                ((RadioButton) a.findViewById(R.id.rbB2)).setChecked(true);
                RadioGroup topics = a.findViewById(R.id.rgTopic);
                for (int i = 0; i < topics.getChildCount(); i++) {
                    RadioButton option = (RadioButton) topics.getChildAt(i);
                    if (option.getText().toString().toLowerCase(java.util.Locale.ROOT).contains("cà phê")) {
                        option.setChecked(true);
                        return;
                    }
                }
                throw new AssertionError("An active coffee topic is needed for this live scenario.");
            });
            click(screen, R.id.btnBeginConversation);
            waitFor(screen, a -> fragment(a) instanceof ChatFragment && a.findViewById(R.id.btnSendMessage).isEnabled());
            JSONArray history = api.get("conversations?page=1&size=20").getJSONArray("result");
            assertEquals(1, history.length());
            long id = history.getJSONObject(0).getLong("id");
            evidence.put("conversationId", id);
            JSONObject detail = api.get("conversations/" + id);
            evidence.put("topicId", detail.getLong("topicId"));
            assertEquals("B2", detail.getString("level"));
            assertEquals(0, detail.getJSONArray("messages").length());
            stage = "real_reply_and_duplicate_click";
            screen.onActivity(a -> {
                input(a, R.id.etMessage, SENTENCES[0]);
                a.findViewById(R.id.btnSendMessage).performClick();
                assertFalse(a.findViewById(R.id.btnSendMessage).isEnabled());
                a.findViewById(R.id.btnSendMessage).performClick();
            });
            waitFor(screen, a -> a.findViewById(R.id.btnSendMessage) != null
                    && a.findViewById(R.id.btnSendMessage).isEnabled()
                    && ((android.widget.LinearLayout) a.findViewById(R.id.messageContainer)).getChildCount() == 2
                    && ((android.widget.TextView) a.findViewById(R.id.tvChatLevel)).getText().toString().contains("1/20"));
            detail = api.get("conversations/" + id);
            JSONArray transcript = detail.getJSONArray("messages");
            assertEquals(2, transcript.length());
            assertEquals(SENTENCES[0], transcript.getJSONObject(0).getString("content"));
            assertEquals("AI", transcript.getJSONObject(1).getString("sender"));
            assertFalse(transcript.getJSONObject(1).getString("content").trim().isEmpty());
            assertEquals(transcript.getJSONObject(0).getString("requestId"), transcript.getJSONObject(1).getString("requestId"));
            String answer = transcript.getJSONObject(1).getString("content");
            waitFor(screen, a -> containsText(a.findViewById(R.id.messageContainer), answer));
            evidence.put("transcript", transcript);
            stage = "recreate_and_restore";
            screen.recreate();
            waitFor(screen, a -> fragment(a) instanceof ChatFragment && containsText(a.findViewById(R.id.messageContainer), answer)
                    && a.findViewById(R.id.btnSendMessage).isEnabled());
            assertEquals(userId, sessions.load().user.getLong("id"));
            assertEquals(transcript.toString(), api.get("conversations/" + id).getJSONArray("messages").toString());
            evidence.put("result", "PASS_CRITICAL_NATIVE_REAL");
        } catch (Throwable error) {
            evidence.put("result", "FAIL").put("failureType", error.getClass().getSimpleName());
            evidence.put("failureFrames", java.util.Arrays.toString(error.getStackTrace()));
            throw new AssertionError("Live UI failed at " + stage + "; see sanitized ui-live-critical.json.");
        } finally {
            evidence.put("lastStage", stage);
            Files.write(new File(context.getNoBackupFilesDir(), "ui-live-critical.json").toPath(),
                    evidence.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void resumeCriticalVerification() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("speakmate.liveVerification")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File report = new File(context.getNoBackupFilesDir(), "ui-live-critical.json");
        JSONObject evidence = new JSONObject(new String(Files.readAllBytes(report.toPath()), StandardCharsets.UTF_8));
        SessionManager sessions = SessionManager.get(context);
        assertEquals(evidence.getLong("userId"), sessions.load().user.getLong("id"));
        LearningApi api = new LearningApi(sessions);
        long id = evidence.getLong("conversationId");
        JSONObject before = api.get("conversations/" + id);
        evidence.put("transcriptBeforeResume", before.getJSONArray("messages"));
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            screen.onActivity(a -> a.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, ChatFragment.newInstance(id)).commit());
            stage = "resume_chat";
            waitFor(screen, a -> fragment(a) instanceof ChatFragment && a.findViewById(R.id.btnSendMessage).isEnabled());
            if (before.getJSONArray("messages").length() == 0) {
                stage = "send_once";
                screen.onActivity(a -> {
                    input(a, R.id.etMessage, SENTENCES[0]);
                    a.findViewById(R.id.btnSendMessage).performClick();
                    assertFalse(a.findViewById(R.id.btnSendMessage).isEnabled());
                    a.findViewById(R.id.btnSendMessage).performClick();
                });
                waitFor(screen, a -> a.findViewById(R.id.btnSendMessage).isEnabled()
                        && ((android.widget.TextView) a.findViewById(R.id.tvChatLevel)).getText().toString().contains("1/20"));
            }
            if (before.getJSONArray("messages").length() == 1) {
                stage = "retry_existing_turn";
                evidence.put("retryExistingTurn", true);
                click(screen, R.id.btnSendMessage);
                waitFor(screen, a -> a.findViewById(R.id.btnSendMessage).isEnabled()
                        && ((android.widget.LinearLayout) a.findViewById(R.id.messageContainer)).getChildCount() == 2);
            }
            stage = "canonical_messages";
            JSONArray transcript = api.get("conversations/" + id).getJSONArray("messages");
            evidence.put("transcript", transcript);
            assertEquals(2, transcript.length());
            assertEquals(SENTENCES[0], transcript.getJSONObject(0).getString("content"));
            assertEquals("AI", transcript.getJSONObject(1).getString("sender"));
            assertFalse(transcript.getJSONObject(1).getString("content").trim().isEmpty());
            assertEquals(transcript.getJSONObject(0).getString("requestId"), transcript.getJSONObject(1).getString("requestId"));
            String answer = transcript.getJSONObject(1).getString("content");
            waitFor(screen, a -> containsText(a.findViewById(R.id.messageContainer), answer));
            stage = "recreate";
            screen.recreate();
            waitFor(screen, a -> fragment(a) instanceof ChatFragment
                    && containsText(a.findViewById(R.id.messageContainer), answer) && a.findViewById(R.id.btnSendMessage).isEnabled());
            assertEquals(transcript.toString(), api.get("conversations/" + id).getJSONArray("messages").toString());
            evidence.put("result", "PASS_CRITICAL_NATIVE_REAL");
        } catch (Throwable error) {
            evidence.put("result", "FAIL").put("failureType", error.getClass().getSimpleName());
            evidence.put("failureFrames", java.util.Arrays.toString(error.getStackTrace()));
            throw new AssertionError("Live UI failed at " + stage);
        } finally {
            evidence.put("lastStage", stage);
            Files.write(report.toPath(), evidence.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    static Fragment fragment(MainActivity activity) {
        return activity.getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
    }

    @Test
    public void diagnosePendingReply() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("speakmate.liveVerification")));
        assertEquals("http://10.0.2.2:8081/", BuildConfig.API_BASE_URL);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file = new File(context.getNoBackupFilesDir(), "ui-live-critical.json");
        JSONObject evidence = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        SessionManager sessions = SessionManager.get(context);
        assertEquals(evidence.getLong("userId"), sessions.load().user.getLong("id"));
        LearningApi api = new LearningApi(sessions);
        long id = evidence.getLong("conversationId");
        JSONObject detail = api.get("conversations/" + id);
        JSONArray transcript = detail.getJSONArray("messages");
        if (transcript.length() == 1) {
            JSONObject message = transcript.getJSONObject(0);
            try {
                JSONObject result = api.send(id, message.getString("content"), message.getString("requestId"));
                evidence.put("diagnosticReplyStatus", result.optString("replyStatus"));
            } catch (com.example.speakmate.auth.AuthApi.ApiException error) {
                evidence.put("diagnosticHttpStatus", error.status).put("diagnosticErrorCode", error.code);
            }
        }
        evidence.put("transcript", api.get("conversations/" + id).getJSONArray("messages"));
        Files.write(file.toPath(), evidence.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void pendingRealTurnSurvivesRecreation() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("speakmate.liveVerification")));
        assertEquals("http://10.0.2.2:8081/", BuildConfig.API_BASE_URL);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File report = new File(context.getNoBackupFilesDir(), "ui-live-critical.json");
        JSONObject evidence = new JSONObject(new String(Files.readAllBytes(report.toPath()), StandardCharsets.UTF_8));
        SessionManager sessions = SessionManager.get(context);
        assertEquals(evidence.getLong("userId"), sessions.load().user.getLong("id"));
        LearningApi api = new LearningApi(sessions);
        long id = evidence.getLong("conversationId");
        JSONArray before = api.get("conversations/" + id).getJSONArray("messages");
        assertEquals(1, before.length());
        String content = before.getJSONObject(0).getString("content");
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            screen.onActivity(a -> a.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, ChatFragment.newInstance(id)).commit());
            waitFor(screen, a -> LiveUiIntegrationTest.containsText(a.findViewById(R.id.messageContainer), content)
                    && a.findViewById(R.id.btnSendMessage).isEnabled());
            screen.recreate();
            waitFor(screen, a -> LiveUiIntegrationTest.containsText(a.findViewById(R.id.messageContainer), content)
                    && a.findViewById(R.id.btnSendMessage).isEnabled());
            assertEquals(before.toString(), api.get("conversations/" + id).getJSONArray("messages").toString());
            evidence.put("realPendingRecreation", "PASS_NO_ADDITIONAL_AI_CALL");
            Files.write(report.toPath(), evidence.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    static void input(MainActivity activity, int id, String text) {
        ((EditText) activity.findViewById(id)).setText(text);
    }

    static void click(ActivityScenario<MainActivity> screen, int id) {
        screen.onActivity(a -> a.findViewById(id).performClick());
    }

    static boolean containsText(android.view.View view, String text) {
        if (view instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView) view).getText())) return true;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (containsText(group.getChildAt(i), text)) return true;
        }
        return false;
    }

    private void waitFor(ActivityScenario<MainActivity> screen, Predicate<MainActivity> check) {
        long deadline = SystemClock.elapsedRealtime() + 70_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean okay = new AtomicBoolean();
            screen.onActivity(a -> okay.set(check.test(a)));
            if (okay.get()) return;
            SystemClock.sleep(200);
        }
        throw new AssertionError("UI timeout at " + stage);
    }
}
