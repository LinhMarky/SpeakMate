package vn.tailinh.speakmate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.tailinh.speakmate.service.ai.*;
import vn.tailinh.speakmate.util.constant.*;

@Tag("mysql")
@SpringBootTest
@AutoConfigureMockMvc
class StudyIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    AiClient ai;

    static final AtomicInteger IPS = new AtomicInteger();

    String token;

    long userId;

    long topicId;

    String topicName;

    final String ip = "203.0.113." + IPS.incrementAndGet();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("TEST_DB_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:13317/speakmate_test(?:\\?.*)?")) {
            throw new IllegalStateException("Study tests require disposable 127.0.0.1:13317/speakmate_test");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
        registry.add("app.auth.jwt-secret", () -> "VEVTVF9PTkxZX05PVF9BX1JFQUxfU0VDUkVUXzEyMzQ1Njc4OTA=");
    }

    @BeforeEach
    void prepare() throws Exception {
        JsonNode account = account();
        token = account.path("access_token").asText();
        userId = account.at("/user/id").asLong();
        topicName = "Coffee " + UUID.randomUUID();
        jdbc.update("INSERT INTO topics(name,level,scenario) VALUES (?, 'A1', 'You are a friendly barista.')",
                topicName);
        topicId = jdbc.queryForObject("SELECT id FROM topics WHERE name=?", Long.class, topicName);
        when(ai.reply(any())).thenReturn("That sounds interesting. What would you like to order today?");
        doAnswer(invocation -> report(invocation.getArgument(0))).when(ai).review(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"C1", "C2"})
    void advancedLevelsPersistReviewVocabularyAndOwnedHistory(String level) throws Exception {
        JsonNode detail = data(postAs(token, "/conversations",
                Map.of("topicId", topicId, "level", level, "createRequestId", uuid()))
                .andExpect(status().isCreated()));
        long id = detail.path("id").asLong();
        assertThat(detail.path("level").asText()).isEqualTo(level);
        for (int i = 0; i < 3; i++) {
            send(id, "I go to the coffee shop yesterday with my friends after school.", uuid())
                    .andExpect(status().isOk());
        }
        doAnswer(invocation -> {
            AiReview original = report(invocation.getArgument(0));
            return new AiReview(false, original.summary(), original.strengths(), original.weaknesses(),
                    EnglishLevel.valueOf(level), original.levelFeedback(), original.grammarCorrections(),
                    original.suggestedWords());
        }).when(ai).review(any());
        JsonNode review = data(postAs(token, "/conversations/" + id + "/finish", null)
                .andExpect(status().isOk()));
        assertThat(review.path("recommendedLevel").asText()).isEqualTo(level);
        assertThat(jdbc.queryForObject("SELECT recommended_level FROM conversations WHERE id=?", String.class, id))
                .isEqualTo(level);
        long suggestionId = review.at("/suggestedWords/0/id").asLong();
        JsonNode saved = data(postAs(token, "/saved-words/from-suggestions",
                Map.of("suggestionIds", List.of(suggestionId))).andExpect(status().isOk()));
        long wordId = saved.at("/created/0/savedWord/id").asLong();
        for (String state : List.of("LEARNING", "MASTERED")) {
            call(patch("/api/v1/saved-words/{id}/review", wordId), token, Map.of("status", state))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(state));
        }
        getAs(token, "/conversations?page=1&size=1").andExpect(jsonPath("$.data.meta.total").value(1))
                .andExpect(jsonPath("$.data.result[0].id").value(id));
        String other = account().path("access_token").asText();
        getAs(other, "/conversations?page=1&size=1").andExpect(jsonPath("$.data.meta.total").value(0));
        getAs(other, "/conversations/" + id).andExpect(status().isNotFound());
        getAs(other, "/conversations/" + id + "/review").andExpect(status().isNotFound());
        call(patch("/api/v1/saved-words/{id}/review", wordId), other, Map.of("status", "MASTERED"))
                .andExpect(status().isNotFound());
        postAs(token, "/conversations", Map.of("topicId", topicId, "level", "C3", "createRequestId", uuid()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminManagesTopicsAndLearnerCannotWriteOrSeeScenario() throws Exception {
        mvc.perform(get("/admin/index.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SpeakMate")));
        mvc.perform(get("/admin/admin.js")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/topics")).andExpect(status().isUnauthorized());
        Map<String, Object> body = Map.of("name", "  New " + UUID.randomUUID() + "  ", "level", "B1", "scenario",
                "You are a receptionist.");
        postAs(token, "/admin/topics", body).andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", userId);
        JsonNode created = data(postAs(token, "/admin/topics", body).andExpect(status().isCreated()));
        long id = created.path("id").asLong();
        assertThat(created.path("name").asText()).isEqualTo(body.get("name").toString().trim());
        postAs(token, "/admin/topics", body).andExpect(status().isConflict());
        postAs(token, "/admin/topics", Map.of("name", "x", "level", "A1", "scenario", "x", "active", false))
                .andExpect(status().isBadRequest());
        call(put("/api/v1/admin/topics/{id}", id), token,
                Map.of("name", "Updated " + UUID.randomUUID(), "level", "B2", "scenario", "New role"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.description").isEmpty());
        call(patch("/api/v1/admin/topics/{id}/active", id), token, Map.of("active", false)).andExpect(status().isOk());
        getAs(token, "/admin/topics").andExpect(status().isOk());
        JsonNode learner = data(getAs(token, "/topics?page=1&size=100").andExpect(status().isOk()));
        for (JsonNode item : learner.path("result")) {
            assertThat(item.path("id").asLong()).isNotEqualTo(id);
            assertThat(item.has("scenario")).isFalse();
        }
        getAs(token, "/topics?page=0").andExpect(status().isBadRequest());
        getAs(token, "/topics?size=101").andExpect(status().isBadRequest());
        getAs(token, "/topics?page=abc").andExpect(status().isBadRequest());
    }

    @Test
    void creationUsesSnapshotsAndReplaysAfterTopicDisabled() throws Exception {
        String uuid = uuid();
        JsonNode c = create(uuid);
        long id = c.path("id").asLong();
        assertThat(c.path("messages").isEmpty()).isTrue();
        assertThat(c.path("level").asText()).isEqualTo("B2");
        assertThat(c.path("profileLevelSnapshot").asText()).isEqualTo("A1");
        jdbc.update("UPDATE topics SET name=?, scenario='Changed scenario',is_active=0 WHERE id=?", "Changed " + uuid(),
                topicId);
        postAs(token, "/conversations", createBody(uuid)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id)).andExpect(jsonPath("$.data.topicName").value(topicName));
        postAs(token, "/conversations", Map.of("topicId", topicId, "level", "A1", "createRequestId", uuid))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("REQUEST_CONFLICT"));
        postAs(token, "/conversations", createBody(uuid())).andExpect(status().isConflict());
        getAs(token, "/conversations/" + id)
                .andExpect(jsonPath("$.data.scenario").value("You are a friendly barista."));
        getAs(token, "/conversations?page=999").andExpect(jsonPath("$.data.result").isEmpty());
        String other = account().path("access_token").asText();
        getAs(other, "/conversations/" + id).andExpect(status().isNotFound());
        postAs(other, "/conversations/" + id + "/messages", turn("Hello", uuid())).andExpect(status().isNotFound());
        postAs(other, "/conversations/" + id + "/finish", null).andExpect(status().isNotFound());
        getAs(other, "/conversations/" + id + "/review").andExpect(status().isNotFound());
        postAs(other, "/conversations/" + id + "/review/retry", null).andExpect(status().isNotFound());
        getAs(other, "/conversations").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.total").value(0)).andExpect(jsonPath("$.data.result").isEmpty());
        getAs(token, "/conversations/" + id).andExpect(jsonPath("$.data.status").value("OPEN"));
        verifyNoInteractions(ai);
    }

    @Test
    void sendIsIdempotentBeforeAndAfterCloseAndNeverExposesCorrections() throws Exception {
        long id = create(uuid()).path("id").asLong();
        String request = uuid();
        JsonNode first = data(send(id, "  I want a coffee.  ", request).andExpect(status().isOk()));
        JsonNode replay = data(send(id, "I want a coffee.", request).andExpect(status().isOk()));
        assertThat(replay.at("/aiMessage/id")).isEqualTo(first.at("/aiMessage/id"));
        assertThat(replay.path("replayed").asBoolean()).isTrue();
        assertThat(first.path("userMessage").has("correctedContent")).isFalse();
        send(id, "Changed", request).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("REQUEST_CONFLICT"));
        postAs(token, "/conversations/" + id + "/finish", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("INSUFFICIENT_DATA"));
        send(id, "I want a coffee.", request).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replyStatus").value("COMPLETED"));
        send(id, "New turn", uuid()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONVERSATION_CLOSED"));
        verify(ai, times(1)).reply(any());
        verify(ai, never()).review(any());
    }

    @Test
    void failedProviderKeepsUserAndRequiresSameRequestForRecovery() throws Exception {
        long id = create(uuid()).path("id").asLong();
        String request = uuid();
        when(ai.reply(any())).thenThrow(AiFailure.timeout()).thenReturn("Sure, what size would you like?");
        send(id, "Coffee please", request).andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value("AI_TIMEOUT"));
        getAs(token, "/conversations/" + id).andExpect(jsonPath("$.data.messages.length()").value(1))
                .andExpect(jsonPath("$.data.pendingReply.status").value("RETRYABLE"));
        send(id, "New content", uuid()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("TURN_PENDING"));
        send(id, "Coffee please", request).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE conversation_id=?", Integer.class, id))
                .isEqualTo(2);
    }

    @Test
    void duplicateWhilePendingReturns202AndFinishDiscardsLateReply() throws Exception {
        long id = create(uuid()).path("id").asLong();
        String request = uuid();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(ai.reply(any())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return "Late reply";
        });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<JsonNode> pending = pool.submit(() -> data(send(id, "Hello", request).andExpect(status().isOk())));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            send(id, "Hello", request).andExpect(status().isAccepted()).andExpect(header().string("Retry-After", "3"))
                    .andExpect(jsonPath("$.data.replyStatus").value("PROCESSING"));
            postAs(token, "/conversations/" + id + "/finish", null).andExpect(status().isOk());
            release.countDown();
            assertThat(pending.get(10, TimeUnit.SECONDS).path("replyStatus").asText()).isEqualTo("CANCELLED");
            send(id, "Hello", request).andExpect(jsonPath("$.data.replyStatus").value("CANCELLED"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE conversation_id=?", Integer.class, id))
                    .isEqualTo(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void expiredReplyCanBeReclaimedButOldAttemptCannotClearOrSaveOverNewOne() throws Exception {
        long id = create(uuid()).path("id").asLong();
        String request = uuid();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(ai.reply(any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                return "Old answer";
            }
            return "New answer";
        });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<JsonNode> first = pool.submit(() -> data(send(id, "Hello", request).andExpect(status().isOk())));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            jdbc.update("UPDATE conversations SET processing_started_at=UTC_TIMESTAMP(6)-INTERVAL 91 SECOND WHERE id=?",
                    id);
            getAs(token, "/conversations/" + id).andExpect(jsonPath("$.data.pendingReply.status").value("RETRYABLE"));
            send(id, "Hello", request).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.aiMessage.content").value("New answer"));
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).at("/aiMessage/content").asText()).isEqualTo("New answer");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE conversation_id=?", Integer.class, id))
                    .isEqualTo(2);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void oldAttemptFailureCannotClearNewReplyClaim() throws Exception {
        long id = create(uuid()).path("id").asLong();
        String request = uuid();
        CountDownLatch firstEntered = new CountDownLatch(1), secondEntered = new CountDownLatch(1);
        CountDownLatch failFirst = new CountDownLatch(1), releaseSecond = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(ai.reply(any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                firstEntered.countDown();
                failFirst.await(10, TimeUnit.SECONDS);
                throw AiFailure.timeout();
            }
            secondEntered.countDown();
            releaseSecond.await(10, TimeUnit.SECONDS);
            return "Recovered";
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> {
                send(id, "Hello", request).andExpect(status().isGatewayTimeout());
                return null;
            });
            assertThat(firstEntered.await(10, TimeUnit.SECONDS)).isTrue();
            jdbc.update("UPDATE conversations SET processing_started_at=UTC_TIMESTAMP(6)-INTERVAL 91 SECOND WHERE id=?",
                    id);
            Future<?> second = pool.submit(() -> {
                send(id, "Hello", request).andExpect(status().isOk());
                return null;
            });
            assertThat(secondEntered.await(10, TimeUnit.SECONDS)).isTrue();
            String tokenBefore = jdbc.queryForObject("SELECT processing_token FROM conversations WHERE id=?",
                    String.class, id);
            failFirst.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT processing_token FROM conversations WHERE id=?", String.class, id))
                    .isEqualTo(tokenBefore);
            releaseSecond.countDown();
            second.get(10, TimeUnit.SECONDS);
        } finally {
            failFirst.countDown();
            releaseSecond.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void fullReportPersistsCorrectionsWordsAndPersonalVocabularyWithoutChangingProfile() throws Exception {
        long id = ready();
        getAs(token, "/conversations/" + id + "/review")
                .andExpect(jsonPath("$.data.reviewStatus").value("NOT_STARTED"));
        verify(ai, never()).review(any());
        JsonNode review = data(postAs(token, "/conversations/" + id + "/finish", null).andExpect(status().isOk()));
        assertThat(review.path("reviewStatus").asText()).isEqualTo("COMPLETED");
        assertThat(review.at("/grammarCorrections/0/originalContent").asText()).startsWith("I go");
        assertThat(jdbc.queryForObject("SELECT level FROM users WHERE id=?", String.class, userId)).isEqualTo("A1");
        postAs(token, "/conversations/" + id + "/finish", null)
                .andExpect(jsonPath("$.data.reviewStatus").value("COMPLETED"));
        postAs(token, "/conversations/" + id + "/review/retry", null)
                .andExpect(jsonPath("$.data.reviewStatus").value("COMPLETED"));
        verify(ai, times(1)).review(any());
        long suggestion = review.at("/suggestedWords/0/id").asLong();
        JsonNode saved = data(
                postAs(token, "/saved-words/from-suggestions", Map.of("suggestionIds", List.of(suggestion)))
                        .andExpect(status().isOk()));
        long wordId = saved.at("/created/0/savedWord/id").asLong();
        assertThat(saved.at("/created/0/savedWord/status").asText()).isEqualTo("NEW");
        postAs(token, "/saved-words/from-suggestions", Map.of("suggestionIds", List.of(suggestion)))
                .andExpect(jsonPath("$.data.existing.length()").value(1));
        getAs(token, "/conversations/" + id + "/review")
                .andExpect(jsonPath("$.data.suggestedWords[0].savedWordId").value(wordId));
        call(patch("/api/v1/saved-words/{id}/review", wordId), token, Map.of("status", "MASTERED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.lastReviewedAt").isNotEmpty());
        call(patch("/api/v1/saved-words/{id}/review", wordId), token, Map.of("status", "NEW"))
                .andExpect(status().isBadRequest());
        getAs(token, "/saved-words?status=MASTERED").andExpect(jsonPath("$.data.meta.total").value(1));
        getAs(token, "/conversations/" + id).andExpect(jsonPath("$.data.messages[0].correctedContent").doesNotExist());
    }

    @Test
    void malformedReportRollsBackEntireReportAndRetryKeepsEndTime() throws Exception {
        long id = ready();
        doAnswer(invocation -> {
            AiReview good = report(invocation.getArgument(0));
            return new AiReview(false, good.summary(), good.strengths(), good.weaknesses(), good.recommendedLevel(),
                    good.levelFeedback(), List.of(new AiReview.Correction(9999999L, "Invented", "Wrong message")),
                    good.suggestedWords());
        }).when(ai).review(any());
        postAs(token, "/conversations/" + id + "/finish", null).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("AI_INVALID_RESPONSE"));
        String ended = jdbc.queryForObject("SELECT CAST(ended_at AS CHAR) FROM conversations WHERE id=?", String.class,
                id);
        getAs(token, "/conversations/" + id + "/review").andExpect(jsonPath("$.data.reviewStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.canRetry").value(true)).andExpect(jsonPath("$.data.summary").isEmpty());
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM word_suggestions WHERE conversation_id=?", Integer.class, id))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE conversation_id=? AND corrected_content IS NOT NULL",
                Integer.class, id)).isZero();
        doAnswer(invocation -> report(invocation.getArgument(0))).when(ai).review(any());
        postAs(token, "/conversations/" + id + "/review/retry", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("COMPLETED"));
        assertThat(jdbc.queryForObject("SELECT CAST(ended_at AS CHAR) FROM conversations WHERE id=?", String.class, id))
                .isEqualTo(ended);
    }

    @Test
    void databaseFailureDuringReportSaveAlsoRollsBackCorrectionsAndCanRetry() throws Exception {
        long id = ready();
        doAnswer(invocation -> {
            AiReview good = report(invocation.getArgument(0));
            // MySQL Unicode collation regards these canonically equivalent strings as the same UNIQUE word.
            return new AiReview(false, good.summary(), good.strengths(), good.weaknesses(), good.recommendedLevel(),
                    good.levelFeedback(), good.grammarCorrections(), List.of(new AiReview.Word("café", "meaning", null),
                            new AiReview.Word("cafe\u0301", "meaning", null)));
        }).when(ai).review(any());
        postAs(token, "/conversations/" + id + "/finish", null).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("AI_INVALID_RESPONSE"));
        getAs(token, "/conversations/" + id + "/review").andExpect(jsonPath("$.data.reviewStatus").value("FAILED"));
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM word_suggestions WHERE conversation_id=?", Integer.class, id))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE conversation_id=? AND corrected_content IS NOT NULL",
                Integer.class, id)).isZero();
    }

    @Test
    void concurrentFinishIsSingleAttemptAndExpiredReviewGetDoesNotMutateOrCallAi() throws Exception {
        long id = ready();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return report(invocation.getArgument(0));
        }).when(ai).review(any());
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> finishing = pool.submit(() -> {
                postAs(token, "/conversations/" + id + "/finish", null).andExpect(status().isOk());
                return null;
            });
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            postAs(token, "/conversations/" + id + "/finish", null).andExpect(status().isAccepted());
            postAs(token, "/conversations/" + id + "/review/retry", null).andExpect(status().isAccepted());
            jdbc.update("UPDATE conversations SET processing_started_at=UTC_TIMESTAMP(6)-INTERVAL 91 SECOND WHERE id=?",
                    id);
            String tokenBefore = jdbc.queryForObject("SELECT processing_token FROM conversations WHERE id=?",
                    String.class, id);
            getAs(token, "/conversations/" + id + "/review").andExpect(jsonPath("$.data.canRetry").value(true))
                    .andExpect(jsonPath("$.data.pollAfterSeconds").value(0));
            assertThat(jdbc.queryForObject("SELECT processing_token FROM conversations WHERE id=?", String.class, id))
                    .isEqualTo(tokenBefore);
            release.countDown();
            finishing.get(10, TimeUnit.SECONDS);
            verify(ai, times(1)).review(any());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void savedWordsAreOwnedBatchAtomicAndConcurrentSavesDoNotDuplicateOrOverwrite() throws Exception {
        long id = ready();
        long suggestion = data(postAs(token, "/conversations/" + id + "/finish", null).andExpect(status().isOk()))
                .at("/suggestedWords/0/id").asLong();
        String other = account().path("access_token").asText();
        postAs(other, "/saved-words/from-suggestions", Map.of("suggestionIds", List.of(suggestion)))
                .andExpect(status().isNotFound());
        postAs(token, "/saved-words/from-suggestions", Map.of("suggestionIds", List.of(suggestion, Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
        postAs(token, "/saved-words/from-suggestions", Map.of("suggestionIds", List.of(suggestion, suggestion)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM saved_words WHERE user_id=?", Integer.class, userId))
                .isZero();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<JsonNode> save = () -> data(
                    postAs(token, "/saved-words/from-suggestions", Map.of("suggestionIds", List.of(suggestion)))
                            .andExpect(status().isOk()));
            Future<JsonNode> a = pool.submit(save), b = pool.submit(save);
            assertThat(a.get(10, TimeUnit.SECONDS).path("created").size()
                    + b.get(10, TimeUnit.SECONDS).path("created").size()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        long wordId = jdbc.queryForObject("SELECT id FROM saved_words WHERE user_id=?", Long.class, userId);
        jdbc.update("UPDATE saved_words SET meaning='Personal meaning' WHERE id=?", wordId);
        postAs(token, "/saved-words/from-suggestions", Map.of("suggestionIds", List.of(suggestion)))
                .andExpect(jsonPath("$.data.existing[0].savedWord.meaning").value("Personal meaning"));
        call(patch("/api/v1/saved-words/{id}/review", wordId), other, Map.of("status", "MASTERED"))
                .andExpect(status().isNotFound());
        getAs(other, "/saved-words").andExpect(jsonPath("$.data.meta.total").value(0));
    }

    @Test
    void twentiethTurnStillReplaysButTwentyFirstIsRejected() throws Exception {
        long id = create(uuid()).path("id").asLong();
        String last = null;
        for (int i = 0; i < 20; i++) {
            last = uuid();
            send(id, "Hello", last).andExpect(status().isOk());
        }
        send(id, "Hello", last).andExpect(status().isOk()).andExpect(jsonPath("$.data.replayed").value(true));
        send(id, "Hello", uuid()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("TURN_LIMIT_REACHED"));
        getAs(token, "/conversations/" + id).andExpect(jsonPath("$.data.userTurnCount").value(20));
        verify(ai, times(20)).reply(any());
    }

    @Test
    void simultaneousCreationWithSameUuidProducesOneConversation() throws Exception {
        String request = uuid();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<JsonNode> create = () -> data(postAs(token, "/conversations", createBody(request)));
            Future<JsonNode> a = pool.submit(create), b = pool.submit(create);
            assertThat(a.get(10, TimeUnit.SECONDS).path("id").asLong())
                    .isEqualTo(b.get(10, TimeUnit.SECONDS).path("id").asLong()).isPositive();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE user_id=? AND create_request_id=?",
                    Integer.class, userId, request)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void revokedSessionCannotPersistReplyAfterProviderReturns() throws Exception {
        long id = create(uuid()).path("id").asLong();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(ai.reply(any())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return "Late answer";
        });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> sending = pool.submit(() -> {
                send(id, "Hello", uuid()).andExpect(status().isUnauthorized());
                return null;
            });
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            postAs(token, "/auth/logout", null).andExpect(status().isOk());
            release.countDown();
            sending.get(10, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE conversation_id=? AND sender='AI'",
                    Integer.class, id)).isZero();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void newReviewAttemptWinsOverLateOldReportAfterLeaseExpires() throws Exception {
        long id = ready();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            AiReview report = report(invocation.getArgument(0));
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                release.await(10, TimeUnit.SECONDS);
                return report;
            }
            return new AiReview(false, "New report", report.strengths(), report.weaknesses(), report.recommendedLevel(),
                    report.levelFeedback(), report.grammarCorrections(), report.suggestedWords());
        }).when(ai).review(any());
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<JsonNode> old = pool.submit(
                    () -> data(postAs(token, "/conversations/" + id + "/finish", null).andExpect(status().isOk())));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            jdbc.update("UPDATE conversations SET processing_started_at=UTC_TIMESTAMP(6)-INTERVAL 91 SECOND WHERE id=?",
                    id);
            postAs(token, "/conversations/" + id + "/review/retry", null).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.summary").value("New report"));
            release.countDown();
            assertThat(old.get(10, TimeUnit.SECONDS).path("summary").asText()).isEqualTo("New report");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM word_suggestions WHERE conversation_id=?",
                    Integer.class, id)).isEqualTo(1);
            verify(ai, times(2)).review(any());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private long ready() throws Exception {
        long id = create(uuid()).path("id").asLong();
        for (int i = 0; i < 3; i++) {
            send(id, "I go to the coffee shop yesterday with my friends after school.", uuid())
                    .andExpect(status().isOk());
        }
        return id;
    }

    private AiReview report(AiConversation input) {
        long firstId = input.messages().stream().filter(m -> m.sender() == MessageSender.USER).findFirst().orElseThrow()
                .id();
        return new AiReview(false, "Đã trao đổi về quán cà phê", "Diễn đạt được nhu cầu", "Cần luyện thì quá khứ",
                EnglishLevel.A1, "Nên luyện A1 thêm trước khi tăng độ khó.",
                List.of(new AiReview.Correction(firstId,
                        "I went to the coffee shop yesterday with my friends after school.",
                        "Dùng went cho sự việc đã xảy ra.")),
                List.of(new AiReview.Word("barista", "nhân viên pha cà phê", "The barista made a coffee.")));
    }

    private JsonNode create(String uuid) throws Exception {
        return data(postAs(token, "/conversations", createBody(uuid)).andExpect(status().isCreated()));
    }

    private Map<String, Object> createBody(String uuid) {
        return Map.of("topicId", topicId, "level", "B2", "createRequestId", uuid);
    }

    private Map<String, Object> turn(String text, String uuid) {
        return Map.of("content", text, "requestId", uuid);
    }

    private ResultActions send(long id, String content, String uuid) throws Exception {
        return postAs(token, "/conversations/" + id + "/messages", turn(content, uuid));
    }

    private JsonNode account() throws Exception {
        String email = uuid() + "@example.test";
        call(post("/api/v1/auth/register"), null,
                Map.of("name", "Study test", "email", email, "password", "Study-pass-2026"))
                .andExpect(status().isCreated());
        return data(call(post("/api/v1/auth/login"), null, Map.of("email", email, "password", "Study-pass-2026"))
                .andExpect(status().isOk()));
    }

    @Test
    void adminManagesRealAccountsAndRevokesBlockedSessions() throws Exception {
        JsonNode learner = account();
        String learnerToken = learner.path("access_token").asText();
        long learnerId = learner.at("/user/id").asLong();
        getAs(learnerToken, "/admin/statistics").andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", userId);
        getAs(token, "/admin/statistics").andExpect(status().isOk()).andExpect(jsonPath("$.data.users").isNumber())
                .andExpect(jsonPath("$.data.completedSessions").isNumber());
        getAs(token, "/admin/users?search=" + learner.at("/user/email").asText()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.total").value(1)).andExpect(jsonPath("$.data.result[0].password").doesNotExist());
        call(patch("/api/v1/admin/users/" + learnerId + "/active"), token, Map.of("active", false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));
        getAs(learnerToken, "/auth/me").andExpect(status().isUnauthorized());
        call(patch("/api/v1/admin/users/" + learnerId + "/active"), token, Map.of("active", true)).andExpect(status().isOk());
        getAs(learnerToken, "/auth/me").andExpect(status().isUnauthorized());
        call(patch("/api/v1/admin/users/" + userId + "/active"), token, Map.of("active", false)).andExpect(status().isBadRequest());
    }

    @Test
    void systemDeckPublishingCopiesWordsWithoutChangingLearnerProgress() throws Exception {
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", userId);
        String name = "System " + uuid();
        String first = "word-" + uuid();
        Map<String, Object> body = Map.of("name", name, "level", "C2", "words", List.of(
                Map.of("word", first, "meaning", "Initial meaning", "example", "Example."),
                Map.of("word", "word-" + uuid(), "meaning", "Second meaning")));
        JsonNode deck = data(postAs(token, "/admin/system-decks", body).andExpect(status().isCreated()));
        long id = deck.path("id").asLong();
        assertThat(deck.path("words").size()).isEqualTo(2);
        JsonNode learner = account();
        String learnerToken = learner.path("access_token").asText();
        long learnerId = learner.at("/user/id").asLong();
        postAs(learnerToken, "/admin/system-decks", body).andExpect(status().isForbidden());
        JsonNode copied = data(postAs(learnerToken, "/system-decks/" + id + "/save", null).andExpect(status().isOk()));
        long savedId = copied.get(0).path("id").asLong();
        assertThat(copied.size()).isEqualTo(2);
        assertThat(data(postAs(learnerToken, "/system-decks/" + id + "/save", null).andExpect(status().isOk()))
                .get(0).path("id").asLong()).isEqualTo(savedId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM saved_words WHERE user_id=?", Integer.class, learnerId)).isEqualTo(2);
        call(patch("/api/v1/saved-words/" + savedId + "/review"), learnerToken, Map.of("status", "MASTERED"))
                .andExpect(status().isOk());
        call(put("/api/v1/admin/system-decks/" + id), token, Map.of("name", name, "level", "C1", "words",
                List.of(Map.of("word", first, "meaning", "Admin changed meaning"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.words.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT meaning FROM saved_words WHERE id=?", String.class, savedId)).isEqualTo("Initial meaning");
        assertThat(jdbc.queryForObject("SELECT status FROM saved_words WHERE id=?", String.class, savedId)).isEqualTo("MASTERED");
        call(patch("/api/v1/admin/system-decks/" + id + "/active"), token, Map.of("active", false)).andExpect(status().isOk());
        postAs(learnerToken, "/system-decks/" + id + "/save", null).andExpect(status().isNotFound());
        getAs(learnerToken, "/system-decks").andExpect(status().isOk());
        postAs(token, "/admin/system-decks", Map.of("name", name, "level", "A1", "words",
                List.of(Map.of("word", "Repeat", "meaning", "one"), Map.of("word", "repeat", "meaning", "two"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminPasswordChangeRequiresOldPasswordAndExpiresTokens() throws Exception {
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", userId);
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id=?", String.class, userId);
        call(put("/api/v1/admin/password"), token, Map.of("currentPassword", "wrong-password", "newPassword", "New-test-pass-2026"))
                .andExpect(status().isBadRequest());
        getAs(token, "/auth/me").andExpect(status().isOk());
        call(put("/api/v1/admin/password"), token, Map.of("currentPassword", "Study-pass-2026", "newPassword", "New-test-pass-2026"))
                .andExpect(status().isOk());
        getAs(token, "/auth/me").andExpect(status().isUnauthorized());
        call(post("/api/v1/auth/login"), null, Map.of("email", email, "password", "Study-pass-2026")).andExpect(status().isUnauthorized());
        call(post("/api/v1/auth/login"), null, Map.of("email", email, "password", "New-test-pass-2026")).andExpect(status().isOk());
    }

    private ResultActions postAs(String auth, String path, Object body) throws Exception {
        return call(post("/api/v1" + path), auth, body);
    }

    private ResultActions getAs(String auth, String path) throws Exception {
        return call(get("/api/v1" + path), auth, null);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String auth, Object body) throws Exception {
        if (auth != null) {
            request.header("Authorization", "Bearer " + auth);
        }
        if (body != null) {
            request.contentType("application/json").content(json.writeValueAsBytes(body));
        }
        return mvc.perform(request.with(req -> {
            req.setRemoteAddr(ip);
            return req;
        }));
    }

    private JsonNode data(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsByteArray()).path("data");
    }

    private String uuid() {
        return UUID.randomUUID().toString();
    }
}
