package com.example.speakmate;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.speakmate.auth.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** HTTP fixture trên emulator; không gọi MySQL hoặc Gemini thật. */
@RunWith(AndroidJUnit4.class)
public class LearningFeaturesTest {
    private Context context;
    private SessionManager sessions;
    private File sessionFile, pendingFile;
    private byte[] previousSession, previousPending;
    private String previousLevel;
    private Fixture server;

    @Before
    public void setup() throws Exception {
        Assume.assumeTrue("Fixture build only", "http://127.0.0.1:18080/".equals(BuildConfig.API_BASE_URL));
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        sessions = SessionManager.get(context);
        sessionFile = new File(context.getNoBackupFilesDir(), "auth-session.bin");
        pendingFile = new File(context.getNoBackupFilesDir(), "pending-user-1.json");
        if (sessionFile.exists()) previousSession = Files.readAllBytes(sessionFile.toPath());
        if (pendingFile.exists()) previousPending = Files.readAllBytes(pendingFile.toPath());
        previousLevel = context.getSharedPreferences("speakmate_profile", 0).getString("practice_level", null);
        sessions.save(new AuthSession("fixture-access", "fixture-refresh", new JSONObject()
                .put("id", 1).put("name", "Fixture Learner").put("email", "fixture@example.test")));
        Files.deleteIfExists(pendingFile.toPath());
        server = new Fixture();
    }

    @After
    public void cleanup() throws Exception {
        if (server != null) server.close();
        if (context == null) return;
        sessions.clear();
        if (previousSession != null) Files.write(sessionFile.toPath(), previousSession);
        if (previousPending == null) Files.deleteIfExists(pendingFile.toPath());
        else Files.write(pendingFile.toPath(), previousPending);
        android.content.SharedPreferences.Editor editor = context.getSharedPreferences("speakmate_profile", 0).edit();
        if (previousLevel == null) editor.remove("practice_level"); else editor.putString("practice_level", previousLevel);
        editor.commit();
    }

    @Test
    public void allSixLevelsAreVisibleAndAdvancedLevelsReachCreateRequest() throws Exception {
        try (ActivityScenario<MainActivity> screen = open(new SpeakingFragment())) {
            waitFor(screen, a -> a.findViewById(R.id.btnBeginConversation).isEnabled());
            for (int id : new int[] {R.id.rbA1, R.id.rbA2, R.id.rbB1, R.id.rbB2, R.id.rbC1, R.id.rbC2}) {
                screen.onActivity(a -> assertEquals(View.VISIBLE, a.findViewById(id).getVisibility()));
            }
            for (String level : new String[] {"C1", "C2"}) {
                screen.onActivity(a -> {
                    ((RadioButton) a.findViewById(level.equals("C1") ? R.id.rbC1 : R.id.rbC2)).setChecked(true);
                    a.findViewById(R.id.btnBeginConversation).performClick();
                });
                waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof ChatFragment
                        && text(a, R.id.tvChatLevel).contains(level));
                assertEquals(level, server.createdLevel);
                if (level.equals("C1")) {
                    replace(screen, new SpeakingFragment());
                    waitFor(screen, a -> a.findViewById(R.id.btnBeginConversation).isEnabled());
                }
            }
            assertEquals(2, server.creates);
        }
    }

    @Test
    public void reviewPollSavesRealSuggestionIdsAndStudyWaitsForAcknowledgement() throws Exception {
        server.processingReads = 1;
        try (ActivityScenario<MainActivity> screen = open(SpeakingResultFragment.newInstance(88))) {
            waitFor(screen, a -> text(a, R.id.tvResultSummary).equals("Fixture review"));
            assertTrue(server.reviewReads >= 2);
            screen.onActivity(a -> {
                ViewGroup words = a.findViewById(R.id.resultVocabularyContainer);
                for (int i = 0; i < words.getChildCount(); i++) {
                    ((AppCompatCheckBox) words.getChildAt(i)).setChecked(true);
                }
                a.findViewById(R.id.btnCreateResultDeck).performClick();
            });
            waitFor(screen, a -> text(a, R.id.tvResultDeckStatus).contains("Đã thêm 1 từ; 1 từ đã có"));
            assertEquals(2, server.savedIds.length());
            assertTrue(server.savedIds.toString().contains("501"));
            assertTrue(server.savedIds.toString().contains("502"));
            screen.onActivity(a -> {
                ViewGroup words = a.findViewById(R.id.resultVocabularyContainer);
                assertFalse(words.getChildAt(0).isEnabled());
                assertFalse(words.getChildAt(1).isEnabled());
                a.findViewById(R.id.btnOpenResultDeck).performClick();
            });
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof FlashcardsFragment
                    && ((ViewGroup) a.findViewById(R.id.deckContainer)).getChildCount() > 0);
            screen.onActivity(a -> ((ViewGroup) a.findViewById(R.id.deckContainer)).getChildAt(0).performClick());
            waitFor(screen, a -> a.findViewById(R.id.btnStudyDeck) != null && a.findViewById(R.id.btnStudyDeck).isEnabled());
            LiveUiIntegrationTest.click(screen, R.id.btnStudyDeck);
            waitFor(screen, a -> text(a, R.id.tvStudyWord).equals("barista"));
            LiveUiIntegrationTest.click(screen, R.id.studyCard);
            screen.onActivity(a -> assertEquals(View.VISIBLE, a.findViewById(R.id.tvStudyMeaning).getVisibility()));
            server.holdPatch = true;
            LiveUiIntegrationTest.click(screen, R.id.btnRemembered);
            assertTrue(server.patchEntered.await(5, TimeUnit.SECONDS));
            screen.onActivity(a -> {
                assertEquals("barista", text(a, R.id.tvStudyWord));
                assertFalse(a.findViewById(R.id.btnRemembered).isEnabled());
                a.findViewById(R.id.btnRemembered).performClick();
            });
            assertEquals(1, server.patches);
            server.patchRelease.countDown();
            waitFor(screen, a -> text(a, R.id.tvStudyWord).equals("receipt"));
            screen.recreate();
            waitFor(screen, a -> text(a, R.id.tvStudyWord).equals("receipt") && a.findViewById(R.id.btnRemembered).isEnabled());
            LiveUiIntegrationTest.click(screen, R.id.btnNotRemembered);
            waitFor(screen, a -> text(a, R.id.tvStudyResult).contains("Đã lưu tiến độ vào tài khoản"));
            assertEquals("MASTERED", server.firstStatus);
            assertEquals("LEARNING", server.secondStatus);
            assertEquals(2, server.patches);
            assertTrue(server.savedPageTwoReads > 0);
        }
    }

    @Test
    public void failedWordUpdateKeepsSameCardForRetry() throws Exception {
        server.saved = true;
        server.failPatch = true;
        try (ActivityScenario<MainActivity> screen = open(StudyFragment.newInstance(FlashcardDeck.SAVED_WORDS_ID))) {
            waitFor(screen, a -> text(a, R.id.tvStudyWord).equals("barista"));
            LiveUiIntegrationTest.click(screen, R.id.btnRemembered);
            waitFor(screen, a -> server.patches == 1 && a.findViewById(R.id.btnRemembered).isEnabled());
            screen.onActivity(a -> assertEquals("barista", text(a, R.id.tvStudyWord)));
            server.failPatch = false;
            LiveUiIntegrationTest.click(screen, R.id.btnRemembered);
            waitFor(screen, a -> text(a, R.id.tvStudyWord).equals("receipt"));
            assertEquals(2, server.patches);
            assertEquals("MASTERED", server.firstStatus);
        }
    }

    @Test
    public void historyPaginatesAndReopensWithoutCreatingOrSending() throws Exception {
        try (ActivityScenario<MainActivity> screen = open(new SpeakingHistoryFragment())) {
            waitFor(screen, a -> historyCount(a) == 2);
            LiveUiIntegrationTest.click(screen, R.id.tvSpeakingHistoryStatus);
            waitFor(screen, a -> historyCount(a) == 3);
            screen.onActivity(a -> historyItem(a, 0).findViewById(R.id.btnOpenHistorySession).performClick());
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof ChatFragment && text(a, R.id.tvChatTopic).equals("Open Coffee"));
            screen.onActivity(a -> a.getSupportFragmentManager().popBackStackImmediate());
            waitFor(screen, a -> historyCount(a) == 2);
            screen.onActivity(a -> {
                assertEquals(View.GONE, historyItem(a, 1).findViewById(R.id.btnDeleteHistorySession).getVisibility());
                historyItem(a, 1).findViewById(R.id.btnOpenHistorySession).performClick();
            });
            waitFor(screen, a -> text(a, R.id.tvResultSummary).equals("Fixture review"));
            LiveUiIntegrationTest.click(screen, R.id.btnViewResultConversation);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof ChatFragment
                    && LiveUiIntegrationTest.containsText(a.findViewById(R.id.messageContainer), "Stored transcript"));
            assertEquals(0, server.creates);
            assertEquals(0, server.messagePosts);
        }
    }

    @Test
    public void expiredReviewDoesNotPollAndOnlyRetriesOnTap() throws Exception {
        server.expiredReview = true;
        try (ActivityScenario<MainActivity> screen = open(SpeakingResultFragment.newInstance(88))) {
            waitFor(screen, a -> text(a, R.id.btnCreateResultDeck).equals("Thử lại nhận xét")
                    && a.findViewById(R.id.btnCreateResultDeck).isEnabled());
            int reads = server.reviewReads;
            SystemClock.sleep(1200);
            assertEquals(reads, server.reviewReads);
            LiveUiIntegrationTest.click(screen, R.id.btnCreateResultDeck);
            waitFor(screen, a -> text(a, R.id.tvResultSummary).equals("Fixture review"));
            assertEquals(1, server.retryPosts);
        }
    }

    private ActivityScenario<MainActivity> open(Fragment fragment) {
        ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class);
        replace(screen, fragment);
        return screen;
    }

    private void replace(ActivityScenario<MainActivity> screen, Fragment fragment) {
        screen.onActivity(a -> a.getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment).commitNow());
    }

    static String text(MainActivity activity, int id) {
        View view = activity.findViewById(id);
        return view instanceof TextView ? ((TextView) view).getText().toString() : "";
    }

    private int historyCount(MainActivity a) {
        ViewGroup list = a.findViewById(R.id.speakingHistoryContainer);
        return list == null ? 0 : list.getChildCount();
    }

    private View historyItem(MainActivity a, int index) {
        return ((ViewGroup) a.findViewById(R.id.speakingHistoryContainer)).getChildAt(index);
    }

    private void waitFor(ActivityScenario<MainActivity> screen, Predicate<MainActivity> check) {
        long deadline = SystemClock.elapsedRealtime() + 15_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean okay = new AtomicBoolean();
            screen.onActivity(a -> okay.set(check.test(a)));
            if (okay.get()) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Learning fixture UI timeout");
    }

    private static final class Fixture implements AutoCloseable {
        private final ServerSocket socket = new ServerSocket();
        private final ExecutorService workers = Executors.newCachedThreadPool();
        volatile int creates, messagePosts, patches, reviewReads, processingReads, retryPosts, savedPageTwoReads;
        volatile boolean saved, failPatch, holdPatch, expiredReview;
        volatile String createdLevel = "A1", firstStatus = "NEW", secondStatus = "MASTERED";
        volatile JSONArray savedIds;
        final CountDownLatch patchEntered = new CountDownLatch(1), patchRelease = new CountDownLatch(1);

        Fixture() throws Exception {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", 18080));
            workers.execute(() -> {
                while (!socket.isClosed()) {
                    try { Socket client = socket.accept(); workers.execute(() -> serve(client)); }
                    catch (IOException ignored) { return; }
                }
            });
        }

        private JSONObject word(int index) throws Exception {
            return new JSONObject().put("id", 701 + index).put("word", index == 0 ? "barista" : "receipt")
                    .put("meaning", index == 0 ? "người pha cà phê" : "hóa đơn")
                    .put("example", "A fixture example.").put("status", index == 0 ? firstStatus : secondStatus);
        }

        private JSONObject page(JSONArray result, int page, int pages, int total) throws Exception {
            return new JSONObject().put("result", result).put("meta", new JSONObject()
                    .put("page", page).put("pages", pages).put("total", total).put("pageSize", 20));
        }

        private JSONObject summary(long id, boolean open) throws Exception {
            return new JSONObject().put("id", id).put("topicName", open ? "Open Coffee" : "Closed Coffee")
                    .put("status", open ? "OPEN" : "CLOSED").put("level", createdLevel)
                    .put("createdAt", "2026-10-30T03:00:00Z").put("reviewStatus", open ? "NOT_STARTED" : "COMPLETED");
        }

        private JSONObject review() throws Exception {
            boolean processing = expiredReview || reviewReads <= processingReads;
            JSONArray words = new JSONArray();
            for (int i = 0; !processing && i < 2; i++) {
                JSONObject row = word(i);
                row.put("id", 501 + i);
                row.remove("status");
                row.put("savedWordId", saved ? 701 + i : JSONObject.NULL);
                words.put(row);
            }
            return new JSONObject().put("conversationId", 88).put("reviewStatus", processing ? "PROCESSING" : "COMPLETED")
                    .put("summary", "Fixture review").put("strengths", "Fixture strength").put("weaknesses", "Fixture weakness")
                    .put("recommendedLevel", "C1").put("levelFeedback", "Fixture level feedback")
                    .put("grammarCorrections", new JSONArray()).put("suggestedWords", words)
                    .put("canRetry", expiredReview).put("pollAfterSeconds", processing && !expiredReview ? 1 : 0);
        }

        private void serve(Socket client) {
            try (client) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                String[] request = reader.readLine().split(" ");
                int length = 0;
                for (String line; (line = reader.readLine()) != null && !line.isEmpty();) {
                    if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
                }
                char[] raw = new char[length];
                int read = 0;
                while (read < length) { int n = reader.read(raw, read, length - read); if (n < 0) break; read += n; }
                JSONObject body = read == 0 ? new JSONObject() : new JSONObject(new String(raw, 0, read));
                String path = request[1];
                JSONObject data;
                if (path.contains("/saved-words/from-suggestions")) {
                    savedIds = body.getJSONArray("suggestionIds");
                    saved = true;
                    data = new JSONObject().put("created", new JSONArray().put(new JSONObject().put("suggestionId", 501).put("savedWord", word(0))))
                            .put("existing", new JSONArray().put(new JSONObject().put("suggestionId", 502).put("savedWord", word(1))));
                } else if (request[0].equals("PATCH")) {
                    patches++;
                    patchEntered.countDown();
                    if (holdPatch) patchRelease.await(10, TimeUnit.SECONDS);
                    if (failPatch) { respond(client, 502, new JSONObject().put("error", "FIXTURE_FAILURE").put("message", "Fixture failed")); return; }
                    int index = path.contains("/701/") ? 0 : 1;
                    if (index == 0) firstStatus = body.getString("status"); else secondStatus = body.getString("status");
                    data = word(index);
                } else if (path.contains("/saved-words?")) {
                    boolean second = path.contains("page=2");
                    if (second) savedPageTwoReads++;
                    data = page(saved ? new JSONArray().put(word(second ? 1 : 0)) : new JSONArray(), second ? 2 : 1, saved ? 2 : 0, saved ? 2 : 0);
                } else if (path.contains("/topics?")) {
                    data = page(new JSONArray().put(new JSONObject().put("id", 9).put("name", "Fixture Coffee").put("level", "A1")), 1, 1, 1);
                } else if (request[0].equals("POST") && path.endsWith("/conversations")) {
                    creates++;
                    createdLevel = body.getString("level");
                    data = summary(77, true);
                } else if (path.contains("/conversations?")) {
                    boolean second = path.contains("page=2");
                    data = page(second ? new JSONArray().put(summary(99, false))
                            : new JSONArray().put(summary(77, true)).put(summary(88, false)), second ? 2 : 1, 2, 3);
                } else if (path.endsWith("/review/retry")) {
                    retryPosts++;
                    expiredReview = false;
                    processingReads = 0;
                    data = review();
                } else if (path.endsWith("/review")) {
                    reviewReads++;
                    data = review();
                } else if (path.endsWith("/messages")) {
                    messagePosts++;
                    data = new JSONObject();
                } else {
                    boolean open = path.endsWith("/77");
                    data = summary(open ? 77 : 88, open).put("userTurnCount", open ? 0 : 3)
                            .put("messages", open ? new JSONArray() : new JSONArray().put(new JSONObject()
                            .put("id", 11).put("sender", "USER").put("content", "Stored transcript").put("requestId", "fixture-request")));
                }
                respond(client, 200, new JSONObject().put("data", data));
            } catch (Exception ignored) { /* Đóng view hoặc test có thể ngắt socket. */ }
        }

        private void respond(Socket client, int status, JSONObject body) throws Exception {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream output = client.getOutputStream();
            output.write(("HTTP/1.1 " + status + " Fixture\r\nContent-Type: application/json\r\nContent-Length: "
                    + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(bytes);
            output.flush();
        }

        @Override public void close() throws Exception {
            patchRelease.countDown();
            socket.close();
            workers.shutdownNow();
        }
    }
}
