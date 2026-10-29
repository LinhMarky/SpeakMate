package vn.tailinh.speakmate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.tailinh.speakmate.service.google.GoogleIdentity;
import vn.tailinh.speakmate.service.google.GoogleIdentityVerifier;
import vn.tailinh.speakmate.service.mail.PasswordResetMailer;

@Tag("mysql")
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthIntegrationTest.AdminProbe.class)
class AuthIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwords;

    @MockitoBean
    GoogleIdentityVerifier google;

    @MockitoBean
    PasswordResetMailer mailer;

    static final AtomicInteger IPS = new AtomicInteger();

    final String ip = "192.0.2." + IPS.incrementAndGet();

    final String email = UUID.randomUUID() + "@example.test";

    final String password = "Original-pass-2026";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("TEST_DB_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:13317/speakmate_test(?:\\?.*)?")) {
            throw new IllegalStateException("Auth tests require disposable 127.0.0.1:13317/speakmate_test");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
        registry.add("app.auth.jwt-secret", () -> "VEVTVF9PTkxZX05PVF9BX1JFQUxfU0VDUkVUXzEyMzQ1Njc4OTA=");
    }

    @Test
    void registrationRestrictsFieldsAndNeverReturnsCredentials() throws Exception {
        JsonNode user = body(postJson("register",
                Map.of("name", "  Nguyễn An  ", "email", email.toUpperCase(), "password", password))
                .andExpect(status().isCreated()));
        assertThat(user.at("/data/email").asText()).isEqualTo(email);
        assertThat(user.at("/data/name").asText()).isEqualTo("Nguyễn An");
        assertThat(user.at("/data/role").asText()).isEqualTo("USER");
        assertThat(user.at("/data/password").isMissingNode()).isTrue();
        String hash = jdbc.queryForObject("SELECT password FROM users WHERE email=?", String.class, email);
        assertThat(passwords.matches(password, hash)).isTrue();
        postJson("register", Map.of("name", "A", "email", email, "password", password))
                .andExpect(status().isConflict());
        postJson("register", Map.of("name", "A", "email", "other-" + email, "password", password, "role", "ADMIN"))
                .andExpect(status().isBadRequest());
        postJson("register", Map.of("name", "A", "email", "other-" + email, "password", "é".repeat(40)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void tokensRotateNewLoginInvalidatesPreviousSessionAndLogoutRevokesAccess() throws Exception {
        register();
        postJson("login", Map.of("email", email, "password", "wrong-password")).andExpect(status().isUnauthorized());
        JsonNode first = login(password);
        String access = first.get("access_token").asText(), refresh = first.get("refresh_token").asText();
        assertThat(jdbc.queryForObject("SELECT refresh_token FROM users WHERE email=?", String.class, email))
                .hasSize(64).isNotEqualTo(refresh);
        me(access).andExpect(status().isOk());
        JsonNode rotated = body(postJson("refresh", Map.of("refreshToken", refresh)).andExpect(status().isOk()))
                .get("data");
        postJson("refresh", Map.of("refreshToken", refresh)).andExpect(status().isUnauthorized());
        JsonNode secondLogin = login(password);
        me(access).andExpect(status().isUnauthorized());
        postJson("refresh", Map.of("refreshToken", rotated.get("refresh_token").asText()))
                .andExpect(status().isUnauthorized());
        String current = secondLogin.get("access_token").asText();
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + current))
                .andExpect(status().isOk());
        me(current).andExpect(status().isUnauthorized());
        postJson("refresh", Map.of("refreshToken", secondLogin.get("refresh_token").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void parallelRefreshConsumesTheOldTokenExactlyOnce() throws Exception {
        register();
        String refresh = login(password).get("refresh_token").asText();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> attempt = () -> {
            start.await();
            return postJson("refresh", Map.of("refreshToken", refresh)).andReturn().getResponse().getStatus();
        };
        try {
            Future<Integer> a = pool.submit(attempt), b = pool.submit(attempt);
            start.countDown();
            assertThat(java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 401);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void blockedUsersLoseAccessAndRolesComeFromDatabase() throws Exception {
        register();
        String access = login(password).get("access_token").asText();
        mvc.perform(get("/api/v1/admin/test-probe").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET role='ADMIN' WHERE email=?", email);
        mvc.perform(get("/api/v1/admin/test-probe").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
        jdbc.update("UPDATE users SET is_active=0 WHERE email=?", email);
        me(access).andExpect(status().isUnauthorized());
        postJson("login", Map.of("email", email, "password", password)).andExpect(status().isForbidden());
    }

    @Test
    void googleCreatesAccountButRequiresExplicitLinkForAnExistingEmail() throws Exception {
        register();
        when(google.verify("valid-google-token")).thenReturn(new GoogleIdentity("sub-" + email, email, "Google User"));
        postJson("google", Map.of("idToken", "valid-google-token")).andExpect(status().isConflict());
        String access = login(password).get("access_token").asText();
        mvc.perform(post("/api/v1/auth/google/link").header("Authorization", "Bearer " + access)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("idToken", "valid-google-token")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.googleLinked").value(true));
        postJson("google", Map.of("idToken", "valid-google-token")).andExpect(status().isOk());
        me(access).andExpect(status().isUnauthorized());
        String newEmail = "google-" + email;
        when(google.verify("new-google-token")).thenReturn(new GoogleIdentity("sub-" + newEmail, newEmail, "New User"));
        postJson("google", Map.of("idToken", "new-google-token")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.passwordEnabled").value(false));
        assertThat(jdbc.queryForObject("SELECT password FROM users WHERE email=?", String.class, newEmail)).isNull();
        postJson("login", Map.of("email", newEmail, "password", password)).andExpect(status().isUnauthorized());
    }

    @Test
    void parallelGoogleLoginsStillLeaveExactlyOneCurrentSession() throws Exception {
        when(google.verify("same-google-token")).thenReturn(new GoogleIdentity("sub-" + email, email, "Google User"));
        postJson("google", Map.of("idToken", "same-google-token")).andExpect(status().isOk());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> attempt = () -> {
            start.await();
            return body(postJson("google", Map.of("idToken", "same-google-token")).andExpect(status().isOk()))
                    .at("/data/access_token").asText();
        };
        try {
            Future<String> a = pool.submit(attempt), b = pool.submit(attempt);
            start.countDown();
            String accessA = a.get(10, TimeUnit.SECONDS), accessB = b.get(10, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT auth_version FROM users WHERE email=?", Long.class, email))
                    .isEqualTo(3L);
            assertThat(java.util.List.of(me(accessA).andReturn().getResponse().getStatus(),
                    me(accessB).andReturn().getResponse().getStatus())).containsExactlyInAnyOrder(200, 401);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void resetUsesOneTimeCodeCommitsFailedAttemptsAndRevokesSession() throws Exception {
        register();
        JsonNode tokens = login(password);
        BlockingQueue<String> deliveries = new LinkedBlockingQueue<>();
        when(mailer.isConfigured()).thenReturn(true);
        doAnswer(invocation -> {
            deliveries.add(invocation.getArgument(1));
            return null;
        }).when(mailer).sendCode(eq(email), anyString(), anyLong());
        postJson("password/forgot", Map.of("email", email)).andExpect(status().isAccepted());
        String code = deliveries.poll(10, TimeUnit.SECONDS);
        assertThat(code).matches("[0-9]{6}");
        String wrong = code.equals("000000") ? "000001" : "000000";
        postJson("password/reset", Map.of("email", email, "code", wrong, "newPassword", "New-pass-2026"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT password_reset_attempts FROM users WHERE email=?", Integer.class, email))
                .isEqualTo(1);
        postJson("password/reset", Map.of("email", email, "code", code, "newPassword", "New-pass-2026"))
                .andExpect(status().isOk());
        me(tokens.get("access_token").asText()).andExpect(status().isUnauthorized());
        postJson("refresh", Map.of("refreshToken", tokens.get("refresh_token").asText()))
                .andExpect(status().isUnauthorized());
        postJson("password/reset", Map.of("email", email, "code", code, "newPassword", "Again-pass-2026"))
                .andExpect(status().isBadRequest());
        postJson("login", Map.of("email", email, "password", password)).andExpect(status().isUnauthorized());
        login("New-pass-2026");
    }

    @Test
    void missingMailConfigurationFailsConsistently() throws Exception {
        register();
        when(mailer.isConfigured()).thenReturn(false);
        postJson("password/forgot", Map.of("email", email)).andExpect(status().isServiceUnavailable());
        postJson("password/forgot", Map.of("email", "unknown-" + email)).andExpect(status().isServiceUnavailable());
        verify(mailer, never()).sendCode(anyString(), anyString(), anyLong());
    }

    private void register() throws Exception {
        postJson("register", Map.of("name", "Test Learner", "email", email, "password", password))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String pass) throws Exception {
        return body(postJson("login", Map.of("email", email, "password", pass)).andExpect(status().isOk())).get("data");
    }

    private ResultActions postJson(String path, Object payload) throws Exception {
        return mvc.perform(post("/api/v1/auth/" + path).with(request -> {
            request.setRemoteAddr(ip);
            return request;
        }).contentType("application/json").content(json.writeValueAsString(payload)));
    }

    private ResultActions me(String access) throws Exception {
        return mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    @RestController
    static class AdminProbe {

        @GetMapping("/api/v1/admin/test-probe")
        Map<String, Boolean> probe() {
            return Map.of("ok", true);
        }
    }
}
