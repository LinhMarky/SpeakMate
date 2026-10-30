package com.example.speakmate;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.speakmate.api.*;
import com.example.speakmate.auth.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Opt-in: 3 reply + 1 review Gemini thật; chỉ tạo dữ liệu cho tài khoản thử mới. */
@RunWith(AndroidJUnit4.class)
public class LiveLearningFeaturesTest {
    private String stage = "prepare";

    @Test
    public void conversationReviewVocabularyStudyAndHistory() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("speakmate.liveVerification")));
        assertEquals("http://10.0.2.2:8081/", BuildConfig.API_BASE_URL);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SessionManager sessions = SessionManager.get(context);
        File sessionFile = new File(context.getNoBackupFilesDir(), "auth-session.bin");
        byte[] previousSession = sessionFile.exists() ? Files.readAllBytes(sessionFile.toPath()) : null;
        String previousLevel = context.getSharedPreferences("speakmate_profile", 0).getString("practice_level", null);
        JSONObject evidence = new JSONObject().put("transport", "Android UI -> Retrofit -> Spring Boot -> real Gemini/MySQL")
                .put("audioVerified", false).put("expectedGeminiCalls", 4);
        String email = "learning-live-" + UUID.randomUUID() + "@example.test";
        String password = UUID.randomUUID().toString() + "aA1!";
        AuthApi auth = new AuthApi();
        long userId = 0;
        sessions.clear();
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            stage = "register_and_login_ui";
            auth.register("Native Learning Check", email, password);
            screen.onActivity(MainActivity::openLogin);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof LoginFragment);
            screen.onActivity(a -> {
                LiveUiIntegrationTest.input(a, R.id.etLoginUsername, email);
                LiveUiIntegrationTest.input(a, R.id.etLoginPassword, password);
                a.findViewById(R.id.btnLogin).performClick();
            });
            waitFor(screen, a -> sessions.load() != null);
            userId = sessions.load().user.getLong("id");
            evidence.put("userId", userId);
            LearningApi api = new LearningApi(sessions);
            stage = "choose_c2_and_coffee";
            screen.onActivity(a -> a.navigateToTab(R.id.nav_speaking));
            waitFor(screen, a -> a.findViewById(R.id.btnBeginConversation) != null
                    && a.findViewById(R.id.btnBeginConversation).isEnabled());
            screen.onActivity(a -> {
                ((RadioButton) a.findViewById(R.id.rbC2)).setChecked(true);
                RadioGroup topics = a.findViewById(R.id.rgTopic);
                boolean selectedCoffee = false;
                for (int i = 0; i < topics.getChildCount(); i++) {
                    RadioButton option = (RadioButton) topics.getChildAt(i);
                    if (option.getText().toString().contains("cà phê")) { option.setChecked(true); selectedCoffee = true; break; }
                }
                assertTrue("Coffee topic must come from backend", selectedCoffee);
                a.findViewById(R.id.btnBeginConversation).performClick();
            });
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof ChatFragment
                    && a.findViewById(R.id.btnSendMessage).isEnabled());
            long[] idHolder = new long[1];
            screen.onActivity(a -> idHolder[0] = LiveUiIntegrationTest.fragment(a).requireArguments().getLong("conversationId"));
            long id = idHolder[0];
            evidence.put("conversationId", id);
            assertEquals("C2", api.get("conversations/" + id).getString("level"));
            for (int i = 0; i < LiveUiIntegrationTest.SENTENCES.length; i++) {
                stage = "real_reply_" + (i + 1);
                String sentence = LiveUiIntegrationTest.SENTENCES[i];
                int expectedMessages = (i + 1) * 2;
                screen.onActivity(a -> {
                    LiveUiIntegrationTest.input(a, R.id.etMessage, sentence);
                    a.findViewById(R.id.btnSendMessage).performClick();
                });
                waitFor(screen, a -> a.findViewById(R.id.btnSendMessage).isEnabled()
                        && ((ViewGroup) a.findViewById(R.id.messageContainer)).getChildCount() == expectedMessages);
            }
            JSONObject detail = api.get("conversations/" + id);
            assertEquals(6, detail.getJSONArray("messages").length());
            stage = "finish_and_real_review";
            LiveUiIntegrationTest.click(screen, R.id.btnEndChat);
            onView(withText("Xem tổng kết")).perform(click());
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof SpeakingResultFragment
                    && a.findViewById(R.id.resultVocabularyContainer) != null
                    && ((ViewGroup) a.findViewById(R.id.resultVocabularyContainer)).getChildCount() > 0);
            JSONObject review = api.get("conversations/" + id + "/review");
            assertEquals("COMPLETED", review.getString("reviewStatus"));
            JSONArray corrections = review.getJSONArray("grammarCorrections");
            for (int i = 0; i < corrections.length(); i++) {
                JSONObject correction = corrections.getJSONObject(i);
                boolean matchesActualUser = false;
                JSONArray transcript = detail.getJSONArray("messages");
                for (int j = 0; j < transcript.length(); j++) {
                    JSONObject message = transcript.getJSONObject(j);
                    if (message.getLong("id") == correction.getLong("messageId") && "USER".equals(message.getString("sender"))) {
                        matchesActualUser = message.getString("content").equals(correction.getString("originalContent"));
                    }
                }
                assertTrue("Correction must reference the actual user's text", matchesActualUser);
            }
            stage = "save_ai_suggestion_ui";
            long suggestionId = review.getJSONArray("suggestedWords").getJSONObject(0).getLong("id");
            screen.onActivity(a -> {
                ViewGroup words = a.findViewById(R.id.resultVocabularyContainer);
                ((AppCompatCheckBox) words.getChildAt(0)).setChecked(true);
                a.findViewById(R.id.btnCreateResultDeck).performClick();
            });
            waitFor(screen, a -> LearningFeaturesTest.text(a, R.id.tvResultDeckStatus).contains("Đã thêm 1 từ"));
            JSONObject freshReview = api.get("conversations/" + id + "/review");
            long wordId = freshReview.getJSONArray("suggestedWords").getJSONObject(0).getLong("savedWordId");
            assertTrue(wordId > 0);
            JSONObject duplicate = api.saveWords(new JSONArray().put(suggestionId));
            assertEquals(0, duplicate.getJSONArray("created").length());
            assertEquals(wordId, duplicate.getJSONArray("existing").getJSONObject(0).getJSONObject("savedWord").getLong("id"));
            evidence.put("savedWordId", wordId);
            stage = "open_saved_flashcard_ui";
            LiveUiIntegrationTest.click(screen, R.id.btnOpenResultDeck);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof FlashcardsFragment
                    && ((ViewGroup) a.findViewById(R.id.deckContainer)).getChildCount() > 0);
            screen.onActivity(a -> ((ViewGroup) a.findViewById(R.id.deckContainer)).getChildAt(0).performClick());
            waitFor(screen, a -> a.findViewById(R.id.btnStudyDeck) != null && a.findViewById(R.id.btnStudyDeck).isEnabled());
            LiveUiIntegrationTest.click(screen, R.id.btnStudyDeck);
            String word = review.getJSONArray("suggestedWords").getJSONObject(0).getString("word");
            waitFor(screen, a -> LearningFeaturesTest.text(a, R.id.tvStudyWord).equals(word));
            LiveUiIntegrationTest.click(screen, R.id.studyCard);
            screen.onActivity(a -> assertEquals(View.VISIBLE, a.findViewById(R.id.tvStudyMeaning).getVisibility()));
            stage = "mastered_ui";
            LiveUiIntegrationTest.click(screen, R.id.btnRemembered);
            waitFor(screen, a -> LearningFeaturesTest.text(a, R.id.tvStudyResult).contains("Đã lưu tiến độ vào tài khoản"));
            assertEquals("MASTERED", api.get("saved-words?page=1&size=20").getJSONArray("result").getJSONObject(0).getString("status"));
            stage = "learning_ui";
            LiveUiIntegrationTest.click(screen, R.id.btnRestartStudy);
            LiveUiIntegrationTest.click(screen, R.id.btnNotRemembered);
            waitFor(screen, a -> LearningFeaturesTest.text(a, R.id.tvStudyResult).contains("Đã lưu tiến độ vào tài khoản"));
            JSONObject savedWord = api.get("saved-words?page=1&size=20").getJSONArray("result").getJSONObject(0);
            assertEquals("LEARNING", savedWord.getString("status"));
            stage = "history_and_transcript_ui";
            screen.onActivity(MainActivity::openSpeakingHistory);
            waitFor(screen, a -> a.findViewById(R.id.speakingHistoryContainer) != null
                    && ((ViewGroup) a.findViewById(R.id.speakingHistoryContainer)).getChildCount() == 1);
            screen.onActivity(a -> ((ViewGroup) a.findViewById(R.id.speakingHistoryContainer)).getChildAt(0)
                    .findViewById(R.id.btnOpenHistorySession).performClick());
            waitFor(screen, a -> LearningFeaturesTest.text(a, R.id.tvResultSummary).equals(review.optString("summary")));
            LiveUiIntegrationTest.click(screen, R.id.btnViewResultConversation);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof ChatFragment
                    && ((ViewGroup) a.findViewById(R.id.messageContainer)).getChildCount() == 6);
            assertEquals(detail.getJSONArray("messages").toString(), api.get("conversations/" + id).getJSONArray("messages").toString());
            stage = "ownership_real_backend";
            String otherEmail = "learning-other-" + UUID.randomUUID() + "@example.test";
            auth.register("Ownership Check", otherEmail, password);
            AuthSession other = auth.login(otherEmail, password);
            ApiClient http = new ApiClient();
            assertEquals(0, http.request("GET", "conversations?page=1&size=20", null, other.accessToken, 15000)
                    .getJSONObject("data").getJSONObject("meta").getInt("total"));
            try {
                http.request("GET", "conversations/" + id, null, other.accessToken, 15000);
                fail("Other account must not read conversation");
            } catch (AuthApi.ApiException error) { assertEquals(404, error.status); }
            try {
                http.request("PATCH", "saved-words/" + wordId + "/review", new JSONObject().put("status", "MASTERED"), other.accessToken, 15000);
                fail("Other account must not update word");
            } catch (AuthApi.ApiException error) { assertEquals(404, error.status); }
            http.request("POST", "auth/logout", new JSONObject(), other.accessToken, 15000);
            evidence.put("detail", api.get("conversations/" + id)).put("review", freshReview).put("savedWord", savedWord)
                    .put("correctionsReferenceActualUserText", true).put("ownershipVerified", true)
                    .put("result", "PASS_NATIVE_REAL_COMPLETE_LEARNING_FLOW");
        } catch (Throwable error) {
            evidence.put("result", "FAIL").put("failureType", error.getClass().getSimpleName());
            String reason = error.getClass().getSimpleName();
            if (error instanceof AuthApi.ApiException) {
                AuthApi.ApiException problem = (AuthApi.ApiException) error;
                evidence.put("httpStatus", problem.status).put("apiErrorCode", problem.code);
                reason = problem.status + " " + problem.code;
            }
            throw new AssertionError("Live learning failed at " + stage + " (" + reason + ")");
        } finally {
            evidence.put("lastStage", stage);
            // Lấy artifact bằng adb pull sau khi chạy test.
            Files.write(new File(context.getExternalFilesDir(null), "learning-live-result.json").toPath(), evidence.toString(2).getBytes(StandardCharsets.UTF_8));
            AuthSession current = sessions.load();
            if (current != null && current.user.optString("email").equals(email)) {
                try { sessions.request("POST", "auth/logout", new JSONObject(), 15000); }
                catch (Exception ignored) { }
            }
            sessions.clear();
            if (previousSession != null) Files.write(sessionFile.toPath(), previousSession);
            android.content.SharedPreferences.Editor editor = context.getSharedPreferences("speakmate_profile", 0).edit();
            if (previousLevel == null) editor.remove("practice_level"); else editor.putString("practice_level", previousLevel);
            editor.commit();
        }
    }

    private void waitFor(ActivityScenario<MainActivity> screen, Predicate<MainActivity> check) {
        long deadline = SystemClock.elapsedRealtime() + 70_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean okay = new AtomicBoolean();
            screen.onActivity(a -> okay.set(check.test(a)));
            if (okay.get()) return;
            SystemClock.sleep(150);
        }
        throw new AssertionError("Live UI timeout at " + stage);
    }
}
