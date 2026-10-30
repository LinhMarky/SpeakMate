package com.example.speakmate;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.speakmate.api.LearningApi;
import com.example.speakmate.auth.AuthSession;
import com.example.speakmate.auth.SessionManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/** Fixture HTTP cục bộ; khôi phục phiên và dữ liệu thiết bị sau mỗi test. */
@RunWith(AndroidJUnit4.class)
public class ProgressStatisticsTest {

    private Context context;
    private SessionManager sessions;
    private File sessionFile;
    private byte[] previousSession;
    private final Map<String, Map<String, ?>> previousPreferences = new HashMap<>();
    private Fixture server;
    private String deckId;

    @Before
    public void setup() throws Exception {
        Assume.assumeTrue("Fixture build only",
                "http://127.0.0.1:18080/".equals(BuildConfig.API_BASE_URL));
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (String name : new String[] {
                "speakmate_local_decks", "speakmate_flashcard_progress", "speakmate_profile"
        }) {
            SharedPreferences preferences = context.getSharedPreferences(name, 0);
            previousPreferences.put(name, new HashMap<>(preferences.getAll()));
            assertTrue(preferences.edit().clear().commit());
        }
        sessions = SessionManager.get(context);
        sessionFile = new File(context.getNoBackupFilesDir(), "auth-session.bin");
        if (sessionFile.exists()) previousSession = Files.readAllBytes(sessionFile.toPath());
        sessions.save(new AuthSession("fixture-access", "fixture-refresh", user()));
        server = new Fixture();
        deckId = new LocalDeckStore(context).createDeck("Fixture linked deck", "A1", Arrays.asList(
                new Flashcard(701, "barista", "người pha cà phê", "A fixture example.", "NEW"),
                new Flashcard(702, "receipt", "hóa đơn", "A fixture example.", "NEW")
        ));
    }

    @After
    public void cleanup() throws Exception {
        if (server != null) server.close();
        if (context == null) return;
        if (sessions != null) {
            sessions.clear();
            if (previousSession != null) Files.write(sessionFile.toPath(), previousSession);
        }
        for (Map.Entry<String, Map<String, ?>> snapshot : previousPreferences.entrySet()) {
            SharedPreferences.Editor editor = context.getSharedPreferences(snapshot.getKey(), 0).edit().clear();
            for (Map.Entry<String, ?> item : snapshot.getValue().entrySet()) {
                Object value = item.getValue();
                if (value instanceof String) editor.putString(item.getKey(), (String) value);
                else if (value instanceof Boolean) editor.putBoolean(item.getKey(), (Boolean) value);
                else if (value instanceof Integer) editor.putInt(item.getKey(), (Integer) value);
                else if (value instanceof Long) editor.putLong(item.getKey(), (Long) value);
                else if (value instanceof Float) editor.putFloat(item.getKey(), (Float) value);
                else if (value instanceof Set) editor.putStringSet(item.getKey(), (Set<String>) value);
            }
            assertTrue(editor.commit());
        }
    }

    @Test
    public void linkedDeckProgressWaitsForServerAndRefreshesAllStatistics() throws Exception {
        server.holdPatch = true;
        try (ActivityScenario<MainActivity> screen = open(StudyFragment.newInstance(deckId))) {
            waitFor(screen, a -> text(a, R.id.tvStudyWord).equals("barista"));
            click(screen, R.id.btnRemembered);
            assertTrue(server.patchEntered.await(5, TimeUnit.SECONDS));
            assertEquals(0, new LocalDeckStore(context).getDecks().get(0).getLearnedCards());
            screen.onActivity(a -> assertFalse(a.findViewById(R.id.btnRemembered).isEnabled()));
            server.patchRelease.countDown();
            waitFor(screen, a -> text(a, R.id.tvStudyWord).equals("receipt"));
            click(screen, R.id.btnRemembered);
            waitFor(screen, a -> a.findViewById(R.id.studyResult).getVisibility() == View.VISIBLE
                    && text(a, R.id.tvStudyResultTitle).contains("Hoàn thành"));
            assertEquals(2, new LocalDeckStore(context).getDecks().get(0).getLearnedCards());
            assertEquals(2, server.patches);

            assertStatistics(screen, 2);
            replace(screen, new FlashcardsFragment());
            waitFor(screen, a -> deckProgress(a, "Fixture linked deck").contains("2/2"));
            assertTrue(deckProgressText(screen, "Từ của tôi").contains("2/2"));

            // Học lại cùng từ không được cộng thêm từ đã nhớ hoặc thêm bộ thẻ.
            replace(screen, StudyFragment.newInstance(deckId));
            waitFor(screen, a -> text(a, R.id.tvStudyWord).equals("barista"));
            click(screen, R.id.btnRemembered);
            waitFor(screen, a -> text(a, R.id.tvStudyWord).equals("receipt"));
            click(screen, R.id.btnRemembered);
            waitFor(screen, a -> a.findViewById(R.id.studyResult).getVisibility() == View.VISIBLE
                    && text(a, R.id.tvStudyResultTitle).contains("Hoàn thành"));
            assertStatistics(screen, 2);
            assertEquals(4, server.patches);
        }
    }

    @Test
    public void failedReviewKeepsOriginalCardAndDoesNotIncreaseLearnedCount() throws Exception {
        server.failPatch = true;
        try (ActivityScenario<MainActivity> screen = open(StudyFragment.newInstance(deckId))) {
            waitFor(screen, a -> text(a, R.id.tvStudyWord).equals("barista"));
            click(screen, R.id.btnRemembered);
            waitFor(screen, a -> server.patches == 1 && a.findViewById(R.id.btnRemembered).isEnabled());
            screen.onActivity(a -> assertEquals("barista", text(a, R.id.tvStudyWord)));
            assertEquals(0, new LocalDeckStore(context).getDecks().get(0).getLearnedCards());
            assertStatistics(screen, 0);
        }
    }

    @Test
    public void freshCloudStatusesUpdateLinkedDeckWithoutDoubleCountingWords() throws Exception {
        server.firstStatus = "MASTERED";
        server.secondStatus = "MASTERED";
        try (ActivityScenario<MainActivity> screen = open(new FlashcardsFragment())) {
            waitFor(screen, a -> deckProgress(a, "Fixture linked deck").contains("2/2"));
            assertTrue(deckProgressText(screen, "Từ của tôi").contains("2/2"));
            assertStatistics(screen, 2);
            assertEquals(0, server.patches);
        }
    }

    private void assertStatistics(ActivityScenario<MainActivity> screen, int remembered) throws Exception {
        replace(screen, new HomeFragment());
        waitFor(screen, a -> text(a, R.id.tvHomeTotalWordCount).equals("2")
                && text(a, R.id.tvLearnedWordCount).equals(String.valueOf(remembered)));
        screen.onActivity(a -> {
            assertEquals("2", text(a, R.id.tvHomeDeckCount));
            assertEquals(String.valueOf(2 - remembered), text(a, R.id.tvHomeRemainingWordCount));
        });
        replace(screen, new ProfileFragment());
        waitFor(screen, a -> text(a, R.id.tvProfileStats).contains("Từ đã nhớ: " + remembered)
                && text(a, R.id.tvProfileStats).contains("Bộ thẻ: 2"));

        FlashcardLibrary.Statistics statistics = FlashcardLibrary.statistics(
                context, new LearningApi(sessions).savedCards());
        assertEquals(2, statistics.deckCount);
        assertEquals(2, statistics.totalWords);
        assertEquals(remembered, statistics.rememberedWords);
        assertEquals(remembered == 2 ? 2 : 0, statistics.completedDeckCount);
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

    private void click(ActivityScenario<MainActivity> screen, int id) {
        screen.onActivity(a -> a.findViewById(id).performClick());
    }

    private static String text(MainActivity activity, int id) {
        View view = activity.findViewById(id);
        return view instanceof TextView ? ((TextView) view).getText().toString() : "";
    }

    private String deckProgressText(ActivityScenario<MainActivity> screen, String name) {
        String[] result = {""};
        screen.onActivity(a -> result[0] = deckProgress(a, name));
        return result[0];
    }

    private static String deckProgress(MainActivity activity, String name) {
        ViewGroup container = activity.findViewById(R.id.deckContainer);
        if (container == null) return "";
        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            TextView title = row.findViewById(R.id.tvDeckName);
            if (title != null && name.contentEquals(title.getText())) {
                return ((TextView) row.findViewById(R.id.tvDeckProgress)).getText().toString();
            }
        }
        return "";
    }

    private void waitFor(ActivityScenario<MainActivity> screen, Predicate<MainActivity> check) {
        long deadline = SystemClock.elapsedRealtime() + 15_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean ready = new AtomicBoolean();
            screen.onActivity(a -> ready.set(check.test(a)));
            if (ready.get()) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Progress fixture UI timeout");
    }

    private static JSONObject user() throws Exception {
        return new JSONObject().put("id", 1).put("name", "Fixture Learner")
                .put("email", "fixture@example.test").put("role", "USER").put("level", "A1");
    }

    private static final class Fixture implements AutoCloseable {
        private final ServerSocket socket = new ServerSocket();
        private final ExecutorService workers = Executors.newCachedThreadPool();
        private volatile boolean failPatch, holdPatch;
        private volatile int patches;
        private volatile String firstStatus = "NEW", secondStatus = "NEW";
        private final CountDownLatch patchEntered = new CountDownLatch(1);
        private final CountDownLatch patchRelease = new CountDownLatch(1);

        Fixture() throws Exception {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", 18080));
            workers.execute(() -> {
                while (!socket.isClosed()) {
                    try {
                        Socket client = socket.accept();
                        workers.execute(() -> serve(client));
                    } catch (IOException ignored) {
                        return;
                    }
                }
            });
        }

        private JSONObject word(int index) throws Exception {
            return new JSONObject().put("id", 701 + index).put("word", index == 0 ? "barista" : "receipt")
                    .put("meaning", index == 0 ? "người pha cà phê" : "hóa đơn")
                    .put("example", "A fixture example.").put("status", index == 0 ? firstStatus : secondStatus);
        }

        private void serve(Socket client) {
            try (client) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                String[] request = reader.readLine().split(" ");
                int length = 0;
                for (String line; (line = reader.readLine()) != null && !line.isEmpty();) {
                    if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                        length = Integer.parseInt(line.substring(15).trim());
                    }
                }
                char[] raw = new char[length];
                int read = 0;
                while (read < length) {
                    int count = reader.read(raw, read, length - read);
                    if (count < 0) break;
                    read += count;
                }
                JSONObject body = read == 0 ? new JSONObject() : new JSONObject(new String(raw, 0, read));
                String path = request[1];
                JSONObject data;
                if (request[0].equals("PATCH") && path.contains("/saved-words/")) {
                    patches++;
                    patchEntered.countDown();
                    if (holdPatch) patchRelease.await(10, TimeUnit.SECONDS);
                    if (failPatch) {
                        respond(client, 502, new JSONObject().put("error", "FIXTURE_FAILURE").put("message", "Fixture failed"));
                        return;
                    }
                    int index = path.contains("/701/") ? 0 : 1;
                    if (index == 0) firstStatus = body.getString("status");
                    else secondStatus = body.getString("status");
                    data = word(index);
                } else if (path.contains("/saved-words?")) {
                    data = new JSONObject().put("result", new JSONArray().put(word(0)).put(word(1)))
                            .put("meta", new JSONObject().put("page", 1).put("pages", 1).put("total", 2).put("pageSize", 100));
                } else {
                    data = user();
                }
                respond(client, 200, new JSONObject().put("data", data));
            } catch (Exception ignored) {
                // Việc đóng activity có thể ngắt request đang chạy trong test.
            }
        }

        private void respond(Socket client, int status, JSONObject body) throws Exception {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream output = client.getOutputStream();
            output.write(("HTTP/1.1 " + status + " Fixture\r\nContent-Type: application/json\r\nContent-Length: "
                    + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(bytes);
            output.flush();
        }

        @Override
        public void close() throws Exception {
            patchRelease.countDown();
            socket.close();
            workers.shutdownNow();
        }
    }
}
