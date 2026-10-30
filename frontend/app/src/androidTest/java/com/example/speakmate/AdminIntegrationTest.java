package com.example.speakmate;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.speakmate.auth.SessionManager;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** HTTP fixture trên emulator, không gọi backend, MySQL hoặc Gemini thật. */
@RunWith(AndroidJUnit4.class)
public class AdminIntegrationTest {
    private SessionManager sessions;
    private File sessionFile;
    private byte[] previousSession;
    private Fixture server;

    @Before
    public void setup() throws Exception {
        Assume.assumeTrue("Fixture build only", "http://127.0.0.1:18080/".equals(BuildConfig.API_BASE_URL));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        sessions = SessionManager.get(context);
        sessionFile = new File(context.getNoBackupFilesDir(), "auth-session.bin");
        if (sessionFile.exists()) previousSession = Files.readAllBytes(sessionFile.toPath());
        sessions.clear();
        server = new Fixture();
    }

    @After
    public void cleanup() throws Exception {
        if (server != null) server.close();
        if (sessions == null) return;
        sessions.clear();
        if (previousSession != null) Files.write(sessionFile.toPath(), previousSession);
    }

    @Test
    public void adminLoginCreatesEditsTogglesAndLogsOut() throws Exception {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            login(screen, "admin@example.test");
            waitFor(screen, a -> "1".equals(text(a, R.id.tvAdminTopicCount)));
            screen.onActivity(a -> {
                assertEquals(View.GONE, a.findViewById(R.id.bottomNavigation).getVisibility());
                assertTrue(a.findViewById(R.id.btnAdminUsers).isEnabled());
                assertTrue(a.findViewById(R.id.btnAdminDecks).isEnabled());
            });
            assertEquals("admin@example.test", server.loginBody.getString("email"));
            assertTrue(sessions.load().isAdmin());
            screen.recreate();
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof AdminDashboardFragment
                    && "1".equals(text(a, R.id.tvAdminTopicCount)));
            openTopics(screen);
            LiveUiIntegrationTest.click(screen, R.id.btnAdminAddTopic);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof AdminTopicFormFragment);
            fillForm(screen, "New topic", "New scenario.");
            server.holdSave = true;
            LiveUiIntegrationTest.click(screen, R.id.btnSaveAdminTopic);
            assertTrue(server.saveEntered.await(5, TimeUnit.SECONDS));
            screen.onActivity(a -> {
                assertFalse(a.findViewById(R.id.btnSaveAdminTopic).isEnabled());
                a.findViewById(R.id.btnSaveAdminTopic).performClick();
            });
            assertEquals(1, server.creates);
            assertEquals("C2", server.saveBody.getString("level"));
            server.saveRelease.countDown();
            waitFor(screen, a -> topicCount(a) == 2);
            screen.onActivity(a -> topic(a, 0).findViewById(R.id.btnAdminTopicEdit).performClick());
            waitFor(screen, a -> "Discuss coffee.".equals(text(a, R.id.etAdminTopicInstructions)));
            screen.onActivity(a -> LiveUiIntegrationTest.input(a, R.id.etAdminTopicInstructions, "Updated scenario."));
            LiveUiIntegrationTest.click(screen, R.id.btnSaveAdminTopic);
            waitFor(screen, a -> topicCount(a) == 2);
            assertEquals(1, server.updates);
            assertEquals(41, server.updatedId);
            assertEquals("Updated scenario.", server.saveBody.getString("scenario"));
            screen.onActivity(a -> topic(a, 0).findViewById(R.id.btnAdminTopicToggleVisibility).performClick());
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
                    .perform(androidx.test.espresso.action.ViewActions.click());
            waitFor(screen, a -> topicCount(a) == 2 && text(topic(a, 0), R.id.tvAdminTopicStatus).equals("Đang ẩn"));
            assertEquals(1, server.patches);
            assertEquals(41, server.patchedId);
            assertFalse(server.activeBody.getBoolean("active"));
            LiveUiIntegrationTest.click(screen, R.id.btnBackAdminTopics);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof AdminDashboardFragment);
            LiveUiIntegrationTest.click(screen, R.id.btnAdminAccount);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof AdminAccountFragment);
            screen.onActivity(a -> assertTrue(text(a, R.id.tvAdminAccountUsername).contains("admin@example.test")));
            LiveUiIntegrationTest.click(screen, R.id.btnAdminLogout);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof LoginFragment);
            assertNull(sessions.load());
            assertEquals(1, server.logouts);
        }
    }

    @Test
    public void rejectedSaveKeepsDraftAndAllowsRetry() throws Exception {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            login(screen, "admin@example.test");
            openTopics(screen);
            LiveUiIntegrationTest.click(screen, R.id.btnAdminAddTopic);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof AdminTopicFormFragment);
            fillForm(screen, "Retry topic", "Keep this draft.");
            server.rejectSave = true;
            LiveUiIntegrationTest.click(screen, R.id.btnSaveAdminTopic);
            waitFor(screen, a -> "Fixture validation failure".equals(text(a, R.id.tvAdminTopicFormStatus))
                    && a.findViewById(R.id.btnSaveAdminTopic).isEnabled());
            screen.onActivity(a -> assertEquals("Keep this draft.", text(a, R.id.etAdminTopicInstructions)));
            server.rejectSave = false;
            LiveUiIntegrationTest.click(screen, R.id.btnSaveAdminTopic);
            waitFor(screen, a -> topicCount(a) == 2);
            assertEquals(2, server.creates);
            assertEquals("Retry topic", server.saveBody.getString("name"));
        }
    }

    @Test
    public void learnerCannotOpenAdminAndServerForbiddenExpiresAdminSession() throws Exception {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            login(screen, "user@example.test");
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof SpeakingFragment);
            assertFalse(sessions.load().isAdmin());
            screen.onActivity(a -> a.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new AdminDashboardFragment()).commitNow());
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof LoginFragment);
            assertNull(sessions.load());
            assertEquals(0, server.adminReads);
            login(screen, "admin@example.test");
            waitFor(screen, a -> "1".equals(text(a, R.id.tvAdminTopicCount)));
            server.forbiddenAdmin = true;
            LiveUiIntegrationTest.click(screen, R.id.btnAdminTopics);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof LoginFragment);
            assertNull(sessions.load());
            assertTrue(server.forbiddenReads > 0);
        }
    }

    private void login(ActivityScenario<MainActivity> screen, String email) {
        screen.onActivity(MainActivity::openLogin);
        waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof LoginFragment);
        screen.onActivity(a -> {
            LiveUiIntegrationTest.input(a, R.id.etLoginUsername, email);
            LiveUiIntegrationTest.input(a, R.id.etLoginPassword, "fixture-only");
        });
        LiveUiIntegrationTest.click(screen, R.id.btnLogin);
        waitFor(screen, a -> email.startsWith("admin")
                ? LiveUiIntegrationTest.fragment(a) instanceof AdminDashboardFragment
                : LiveUiIntegrationTest.fragment(a) instanceof SpeakingFragment);
    }

    @Test
    public void realAdminScreensSendUserDeckAndPasswordRequests() throws Exception {
        try (ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class)) {
            login(screen, "admin@example.test");
            waitFor(screen, a -> "2".equals(text(a, R.id.tvAdminUserCount)));
            LiveUiIntegrationTest.click(screen, R.id.btnAdminUsers);
            waitFor(screen, a -> a.findViewById(R.id.adminUserListContainer) instanceof ViewGroup
                    && ((ViewGroup) a.findViewById(R.id.adminUserListContainer)).getChildCount() == 1);
            screen.onActivity(a -> ((ViewGroup) a.findViewById(R.id.adminUserListContainer)).getChildAt(0)
                    .findViewById(R.id.btnAdminUserToggleStatus).performClick());
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
                    .perform(androidx.test.espresso.action.ViewActions.click());
            waitFor(screen, a -> server.userPatches == 1);
            assertFalse(server.learnerActive);
            LiveUiIntegrationTest.click(screen, R.id.btnBackAdminUsers);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof AdminDashboardFragment);
            LiveUiIntegrationTest.click(screen, R.id.btnAdminDecks);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof AdminDecksFragment
                    && a.findViewById(R.id.btnAdminAddDeck).isEnabled());
            LiveUiIntegrationTest.click(screen, R.id.btnAdminAddDeck);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof AdminDeckFormFragment);
            screen.onActivity(a -> {
                LiveUiIntegrationTest.input(a, R.id.etAdminDeckName, "Fixture deck");
                LiveUiIntegrationTest.input(a, R.id.etAdminCardWord, "barista");
                LiveUiIntegrationTest.input(a, R.id.etAdminCardMeaning, "Coffee maker");
                LiveUiIntegrationTest.input(a, R.id.etAdminCardExample, "I am a barista.");
                a.findViewById(R.id.btnAddAdminCard).performClick();
                a.findViewById(R.id.btnSaveAdminDeck).performClick();
            });
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof AdminDecksFragment
                    && a.findViewById(R.id.adminDeckListContainer) instanceof ViewGroup
                    && ((ViewGroup) a.findViewById(R.id.adminDeckListContainer)).getChildCount() == 1);
            assertEquals(1, server.deckCreates);
            assertEquals("barista", server.deckBody.getJSONArray("words").getJSONObject(0).getString("word"));
            screen.onActivity(a -> ((ViewGroup) a.findViewById(R.id.adminDeckListContainer)).getChildAt(0)
                    .findViewById(R.id.btnAdminDeckEdit).performClick());
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof AdminDeckFormFragment);
            screen.onActivity(a -> LiveUiIntegrationTest.input(a, R.id.etAdminDeckName, "Updated deck"));
            LiveUiIntegrationTest.click(screen, R.id.btnSaveAdminDeck);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof AdminDecksFragment && server.deckUpdates == 1);
            waitFor(screen, a -> a.findViewById(R.id.adminDeckListContainer) instanceof ViewGroup
                    && ((ViewGroup) a.findViewById(R.id.adminDeckListContainer)).getChildCount() == 1);
            screen.onActivity(a -> ((ViewGroup) a.findViewById(R.id.adminDeckListContainer)).getChildAt(0)
                    .findViewById(R.id.btnAdminDeckToggleVisibility).performClick());
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
                    .perform(androidx.test.espresso.action.ViewActions.click());
            waitFor(screen, a -> server.deckPatches == 1);
            assertFalse(server.systemDeck.getBoolean("active"));
            LiveUiIntegrationTest.click(screen, R.id.btnBackAdminDecks);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof AdminDashboardFragment);
            LiveUiIntegrationTest.click(screen, R.id.btnAdminAccount);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof AdminAccountFragment);
            LiveUiIntegrationTest.click(screen, R.id.btnAdminChangePassword);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof AdminChangePasswordFragment);
            screen.onActivity(a -> {
                LiveUiIntegrationTest.input(a, R.id.etAdminCurrentPassword, "fixture-only");
                LiveUiIntegrationTest.input(a, R.id.etAdminNewPassword, "fixture-new-password");
                LiveUiIntegrationTest.input(a, R.id.etAdminConfirmPassword, "fixture-new-password");
            });
            LiveUiIntegrationTest.click(screen, R.id.btnSaveAdminPassword);
            waitFor(screen, a -> LiveUiIntegrationTest.fragment(a) instanceof LoginFragment);
            assertEquals(1, server.passwordChanges);
            assertNull(sessions.load());
        }
    }

    private void openTopics(ActivityScenario<MainActivity> screen) {
        LiveUiIntegrationTest.click(screen, R.id.btnAdminTopics);
        waitFor(screen, a -> topicCount(a) == 1);
    }

    private void fillForm(ActivityScenario<MainActivity> screen, String name, String scenario) {
        screen.onActivity(a -> {
            LiveUiIntegrationTest.input(a, R.id.etAdminTopicName, name);
            LiveUiIntegrationTest.input(a, R.id.etAdminTopicDescription, "Fixture description");
            LiveUiIntegrationTest.input(a, R.id.etAdminTopicInstructions, scenario);
            ((Spinner) a.findViewById(R.id.spAdminTopicLevel)).setSelection(5);
        });
    }

    private static String text(MainActivity activity, int id) {
        View view = activity.findViewById(id);
        return view instanceof TextView ? ((TextView) view).getText().toString() : "";
    }

    private static String text(View root, int id) {
        return ((TextView) root.findViewById(id)).getText().toString();
    }

    private static int topicCount(MainActivity activity) {
        ViewGroup list = activity.findViewById(R.id.adminTopicListContainer);
        return list == null ? 0 : list.getChildCount();
    }

    private static View topic(MainActivity activity, int index) {
        return ((ViewGroup) activity.findViewById(R.id.adminTopicListContainer)).getChildAt(index);
    }

    private void waitFor(ActivityScenario<MainActivity> screen, Predicate<MainActivity> check) {
        long deadline = SystemClock.elapsedRealtime() + 12_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean okay = new AtomicBoolean();
            screen.onActivity(a -> okay.set(check.test(a)));
            if (okay.get()) return;
            SystemClock.sleep(80);
        }
        fail("Admin fixture timed out; server error=" + server.errorType);
    }

    private static final class Fixture implements AutoCloseable {
        private final ServerSocket socket = new ServerSocket();
        private final ExecutorService workers = Executors.newCachedThreadPool();
        private final JSONArray rows = new JSONArray();
        volatile int creates, updates, patches, logouts, adminReads, forbiddenReads;
        volatile int userPatches, deckCreates, deckUpdates, deckPatches, passwordChanges;
        volatile boolean learnerActive = true;
        volatile JSONObject systemDeck, deckBody;
        volatile long updatedId, patchedId;
        volatile boolean holdSave, rejectSave, forbiddenAdmin;
        volatile JSONObject loginBody, saveBody, activeBody;
        volatile String errorType = "none";
        final CountDownLatch saveEntered = new CountDownLatch(1), saveRelease = new CountDownLatch(1);

        Fixture() throws Exception {
            rows.put(new JSONObject().put("id", 41).put("name", "Fixture coffee").put("description", "Coffee topic")
                    .put("level", "A2").put("scenario", "Discuss coffee.").put("active", true));
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", 18080));
            workers.execute(() -> {
                while (!socket.isClosed()) {
                    try { Socket client = socket.accept(); workers.execute(() -> serve(client)); }
                    catch (IOException ignored) { return; }
                }
            });
        }

        private void serve(Socket client) {
            try (client) {
                client.setSoTimeout(10_000);
                InputStream input = client.getInputStream();
                String[] request = line(input).split(" ");
                int length = 0;
                for (String header; !(header = line(input)).isEmpty();) {
                    if (header.toLowerCase(Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(header.substring(15).trim());
                }
                byte[] bytes = new byte[length];
                for (int offset = 0; offset < length;) {
                    int read = input.read(bytes, offset, length - offset);
                    if (read < 0) throw new EOFException();
                    offset += read;
                }
                JSONObject body = length == 0 ? new JSONObject() : new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                String method = request[0], path = request[1];
                JSONObject data;
                int status = 200;
                if (path.endsWith("/auth/login")) {
                    loginBody = body;
                    boolean admin = body.getString("email").startsWith("admin");
                    data = new JSONObject().put("access_token", "fixture-access").put("refresh_token", "fixture-refresh")
                            .put("user", new JSONObject().put("id", admin ? 901 : 902).put("name", "Fixture account")
                            .put("email", body.getString("email")).put("role", admin ? "ADMIN" : "USER")
                            .put("passwordEnabled", true).put("level", "A1"));
                } else if (path.endsWith("/auth/logout")) {
                    logouts++;
                    data = new JSONObject();
                } else if (path.endsWith("/admin/statistics")) {
                    data = new JSONObject().put("users", 2).put("topics", rows.length()).put("systemDecks", systemDeck == null ? 0 : 1).put("completedSessions", 0);
                } else if (path.contains("/admin/users")) {
                    JSONObject learner = new JSONObject().put("id", 902).put("name", "Fixture learner")
                            .put("email", "learner@example.test").put("role", "USER").put("level", "A1");
                    if (method.equals("PATCH")) { learnerActive = body.getBoolean("active"); userPatches++; }
                    learner.put("active", learnerActive);
                    data = method.equals("PATCH") ? learner : page(new JSONArray().put(learner));
                } else if (path.contains("/admin/system-decks")) {
                    if (method.equals("GET")) data = page(systemDeck == null ? new JSONArray() : new JSONArray().put(systemDeck));
                    else if (method.equals("PATCH")) { systemDeck.put("active", body.getBoolean("active")); deckPatches++; data = systemDeck; }
                    else {
                        deckBody = body;
                        if (method.equals("POST")) deckCreates++; else deckUpdates++;
                        systemDeck = new JSONObject(body.toString()).put("id", 61).put("active", true);
                        data = systemDeck;
                    }
                } else if (path.endsWith("/admin/password")) {
                    passwordChanges++;
                    data = new JSONObject();
                } else if (path.contains("/admin/topics") && forbiddenAdmin) {
                    forbiddenReads++;
                    respond(client, 403, new JSONObject().put("error", "FORBIDDEN").put("message", "Fixture forbidden"));
                    return;
                } else if (method.equals("GET")) {
                    if (path.contains("/admin/topics")) adminReads++;
                    data = new JSONObject().put("result", rows).put("meta", new JSONObject()
                            .put("page", 1).put("pages", 1).put("pageSize", 20).put("total", rows.length()));
                } else if (path.contains("/admin/topics")) {
                    if (method.equals("PATCH")) {
                        patches++;
                        patchedId = Long.parseLong(path.split("/topics/")[1].split("/")[0]);
                        activeBody = body;
                        data = rows.getJSONObject(0).put("active", body.getBoolean("active"));
                    } else {
                        saveBody = body;
                        if (method.equals("POST")) creates++; else updates++;
                        saveEntered.countDown();
                        if (holdSave) saveRelease.await(8, TimeUnit.SECONDS);
                        if (rejectSave) {
                            respond(client, 400, new JSONObject().put("error", "VALIDATION_ERROR").put("message", "Fixture validation failure"));
                            return;
                        }
                        long id = method.equals("POST") ? 42 : Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
                        data = new JSONObject(body.toString()).put("id", id).put("active", true);
                        if (method.equals("POST")) { rows.put(data); status = 201; }
                        else { updatedId = id; rows.put(0, data); }
                    }
                } else {
                    throw new IOException("Unexpected fixture request");
                }
                respond(client, status, new JSONObject().put("statusCode", status).put("message", "Fixture response").put("data", data));
            } catch (Exception error) { errorType = error.getClass().getSimpleName(); }
        }

        private String line(InputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            for (int value; (value = input.read()) != -1;) {
                if (value == '\n') return bytes.toString(StandardCharsets.US_ASCII.name()).replace("\r", "");
                bytes.write(value);
                if (bytes.size() > 16_384) throw new IOException("Fixture header too large");
            }
            throw new EOFException();
        }

        private JSONObject page(JSONArray items) throws Exception {
            return new JSONObject().put("result", items).put("meta", new JSONObject()
                    .put("page", 1).put("pages", items.length() == 0 ? 0 : 1).put("pageSize", 20).put("total", items.length()));
        }

        private void respond(Socket client, int status, JSONObject body) throws IOException {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream output = client.getOutputStream();
            output.write(("HTTP/1.1 " + status + " Fixture\r\nContent-Type: application/json\r\nContent-Length: "
                    + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(bytes);
            output.flush();
        }

        @Override public void close() throws Exception {
            saveRelease.countDown();
            socket.close();
            workers.shutdownNow();
        }
    }
}
