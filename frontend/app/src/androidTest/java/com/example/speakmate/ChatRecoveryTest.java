package com.example.speakmate;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.speakmate.api.ApiClient;
import com.example.speakmate.auth.AuthApi;
import com.example.speakmate.auth.AuthSession;
import com.example.speakmate.auth.SessionManager;
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

/** HTTP fixture riêng trên emulator; không dùng Gemini hay MySQL thật. */
@RunWith(AndroidJUnit4.class)
public class ChatRecoveryTest {
    private Context context;
    private SessionManager sessions;
    private File sessionFile, pendingFile;
    private byte[] previousSession, previousPending;
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
    }

    @Test
    public void unavailableAiRetriesSameUuidWithoutDuplicatingUserMessage() throws Exception {
        retryFailure("unavailable");
    }

    @Test
    public void malformedResponseKeepsOriginalTurnForRetry() throws Exception {
        retryFailure("malformed");
    }

    @Test
    public void aiTimeoutKeepsOriginalTurnForRetry() throws Exception {
        retryFailure("timeout");
    }

    private void retryFailure(String mode) throws Exception {
        server.mode = mode;
        try (ActivityScenario<MainActivity> screen = chat()) {
            send(screen);
            waitFor(screen, a -> "Thử lại".contentEquals(((TextView) a.findViewById(R.id.btnSendMessage)).getText())
                    && a.findViewById(R.id.btnSendMessage).isEnabled());
            assertEquals(1, server.userCount);
            String uuid = server.requestId;
            server.mode = "ok";
            LiveUiIntegrationTest.click(screen, R.id.btnSendMessage);
            replyVisible(screen);
            assertEquals(uuid, server.requestId);
            assertEquals(1, server.userCount);
            assertEquals(2, server.posts);
        }
    }

    @Test
    public void lostResponseReadsSavedAnswerWithoutResubmitting() throws Exception {
        server.mode = "lost";
        try (ActivityScenario<MainActivity> screen = chat()) {
            send(screen);
            replyVisible(screen);
            assertEquals(1, server.posts);
            assertEquals(1, server.userCount);
        }
    }

    @Test
    public void navigationDuringRequestRestoresCanonicalMessages() throws Exception {
        server.mode = "hold";
        try (ActivityScenario<MainActivity> screen = chat()) {
            send(screen);
            assertTrue(server.received.await(5, TimeUnit.SECONDS));
            screen.onActivity(a -> a.navigateToTab(R.id.nav_speaking));
            server.release.countDown();
            assertTrue(server.completed.await(5, TimeUnit.SECONDS));
            screen.onActivity(a -> a.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, ChatFragment.newInstance(77)).commit());
            replyVisible(screen);
            assertEquals(1, server.posts);
            assertEquals(1, server.userCount);
        }
    }

    @Test
    public void socketReadTimeoutReturnsSafeNetworkError() throws Exception {
        try {
            new ApiClient().request("GET", "slow", null, null, 200);
            fail("Expected socket timeout");
        } catch (AuthApi.ApiException error) {
            assertEquals(0, error.status);
            assertEquals("NETWORK_TIMEOUT", error.code);
        }
    }

    private ActivityScenario<MainActivity> chat() {
        ActivityScenario<MainActivity> screen = ActivityScenario.launch(MainActivity.class);
        screen.onActivity(a -> a.getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, ChatFragment.newInstance(77)).commit());
        waitFor(screen, a -> a.findViewById(R.id.btnSendMessage) != null && a.findViewById(R.id.btnSendMessage).isEnabled());
        return screen;
    }

    private void send(ActivityScenario<MainActivity> screen) {
        screen.onActivity(a -> {
            LiveUiIntegrationTest.input(a, R.id.etMessage, "I would like a coffee, please.");
            a.findViewById(R.id.btnSendMessage).performClick();
            assertFalse(a.findViewById(R.id.btnSendMessage).isEnabled());
            a.findViewById(R.id.btnSendMessage).performClick();
        });
    }

    private void replyVisible(ActivityScenario<MainActivity> screen) {
        waitFor(screen, a -> LiveUiIntegrationTest.containsText(a.findViewById(R.id.messageContainer), "Fixture reply.")
                && a.findViewById(R.id.btnSendMessage).isEnabled());
    }

    private void waitFor(ActivityScenario<MainActivity> screen, Predicate<MainActivity> check) {
        long deadline = SystemClock.elapsedRealtime() + 20_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean result = new AtomicBoolean();
            screen.onActivity(a -> result.set(check.test(a)));
            if (result.get()) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Fixture UI timeout");
    }

    private static final class Fixture implements AutoCloseable {
        private final ServerSocket socket = new ServerSocket();
        private final ExecutorService workers = Executors.newCachedThreadPool();
        volatile String mode = "ok", requestId, content;
        volatile int posts, userCount;
        volatile boolean answered;
        final CountDownLatch received = new CountDownLatch(1), release = new CountDownLatch(1), completed = new CountDownLatch(1);

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

        private void serve(Socket client) {
            try (client) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                String[] request = reader.readLine().split(" ");
                int length = 0;
                for (String line; (line = reader.readLine()) != null && !line.isEmpty();) {
                    if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
                }
                char[] body = new char[length];
                int read = 0;
                while (read < length) { int n = reader.read(body, read, length - read); if (n < 0) break; read += n; }
                String path = request[1];
                if (path.endsWith("/slow")) { Thread.sleep(1200); respond(client, 200, "{}"); return; }
                if (request[0].equals("POST") && path.endsWith("/messages")) {
                    JSONObject json = new JSONObject(new String(body));
                    synchronized (this) {
                        posts++;
                        String uuid = json.getString("requestId");
                        if (requestId == null) { requestId = uuid; content = json.getString("content"); userCount++; }
                        else if (!uuid.equals(requestId)) userCount++;
                    }
                    received.countDown();
                    if (mode.equals("hold")) release.await(5, TimeUnit.SECONDS);
                    if (mode.equals("unavailable") || mode.equals("timeout")) {
                        respond(client, mode.equals("timeout") ? 504 : 503,
                                new JSONObject().put("error", mode.equals("timeout") ? "AI_TIMEOUT" : "AI_UNAVAILABLE")
                                        .put("message", "Fixture service failure").toString());
                        return;
                    }
                    if (mode.equals("malformed")) { respond(client, 200, "{bad-json"); return; }
                    answered = true;
                    completed.countDown();
                    if (mode.equals("lost")) return;
                    respond(client, 200, new JSONObject().put("data", new JSONObject().put("replyStatus", "COMPLETED")).toString());
                    return;
                }
                JSONObject data;
                if (path.contains("/topics")) data = new JSONObject().put("result", new JSONArray()).put("meta", new JSONObject().put("pages", 1));
                else data = detail();
                respond(client, 200, new JSONObject().put("data", data).toString());
            } catch (Exception ignored) { /* Client có thể rời màn hình trước khi server xử lý xong. */ }
        }

        private JSONObject detail() throws Exception {
            JSONArray messages = new JSONArray();
            if (requestId != null) messages.put(new JSONObject().put("id", 1).put("sender", "USER").put("content", content).put("requestId", requestId));
            if (answered) messages.put(new JSONObject().put("id", 2).put("sender", "AI").put("content", "Fixture reply.").put("requestId", requestId));
            JSONObject data = new JSONObject().put("id", 77).put("status", "OPEN").put("level", "A2")
                    .put("topicName", "Fixture Coffee").put("userTurnCount", userCount).put("messages", messages);
            if (requestId != null && !answered) data.put("pendingReply", new JSONObject().put("userMessageId", 1)
                    .put("requestId", requestId).put("status", mode.equals("hold") ? "PROCESSING" : "RETRYABLE"));
            return data;
        }

        private void respond(Socket client, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            OutputStream out = client.getOutputStream();
            out.write(("HTTP/1.1 " + status + " Fixture\r\nContent-Type: application/json\r\nContent-Length: "
                    + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(bytes); out.flush();
        }

        public void close() throws Exception { release.countDown(); socket.close(); workers.shutdownNow(); }
    }
}
