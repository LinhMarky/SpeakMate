package vn.tailinh.speakmate.service.ai;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.tailinh.speakmate.config.AiProperties;
import vn.tailinh.speakmate.exception.BusinessException;
import vn.tailinh.speakmate.util.constant.MessageSender;

@Component
public class GeminiAiClient implements AiClient {

    private final AiProperties properties;

    private final ObjectMapper json;

    private final PromptBuilder prompts;

    private final HttpClient http;

    @Autowired
    public GeminiAiClient(AiProperties properties, ObjectMapper json, PromptBuilder prompts) {
        this(properties, json, prompts, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    GeminiAiClient(AiProperties properties, ObjectMapper json, PromptBuilder prompts, HttpClient http) {
        this.properties = properties;
        this.json = json;
        this.prompts = prompts;
        this.http = http;
    }

    @Override
    public String reply(AiConversation input) {
        var contents = input.messages().stream().map(m -> Map.of("role",
                m.sender() == MessageSender.USER ? "user" : "model", "parts", List.of(Map.of("text", m.content()))))
                .toList();
        return generate(prompts.reply(input), contents, Map.of("maxOutputTokens", 512), 20);
    }

    @Override
    public AiReview review(AiConversation input) {
        // Ép AI trả JSON theo schema; ReviewValidator kiểm tra nghiệp vụ trước khi lưu.
        try (var schemaStream = GeminiAiClient.class.getResourceAsStream("/ai/review-schema.json")) {
            if (schemaStream == null) {
                throw new IllegalStateException("Missing review schema resource");
            }
            JsonNode schema = json.readTree(schemaStream);
            String transcript = json.writeValueAsString(input.messages());
            String output = generate(prompts.review(input),
                    List.of(Map.of("role", "user", "parts",
                            List.of(Map.of("text", "Evaluate this completed transcript: " + transcript)))),
                    Map.of("maxOutputTokens", 8192, "responseMimeType", "application/json", "responseJsonSchema",
                            schema),
                    45);
            return json.readerFor(AiReview.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(output);
        } catch (java.io.IOException exception) {
            throw AiFailure.invalid();
        }
    }

    @Override
    public String generateVocabulary(String topic, String level, int count) {
        String system = prompts.generateVocabulary(topic, level, count);
        var contents = List.of(Map.of("role", "user", "parts",
                List.of(Map.of("text", "Generate vocabulary for topic: " + topic))));
        return generate(system, contents,
                Map.of("maxOutputTokens", 4096, "responseMimeType", "application/json"), 30);
    }

    String generate(String system, Object contents, Map<String, Object> config, int timeoutSeconds) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED",
                    "Backend chưa được cấu hình Gemini API key");
        }
        if (properties.getModel() == null || !properties.getModel().matches("[a-zA-Z0-9.-]{1,100}")) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED",
                    "Tên model AI chưa hợp lệ");
        }
        CompletableFuture<HttpResponse<String>> pending = null;
        try {
            String body = json
                    .writeValueAsString(Map.of("systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                            "contents", contents, "generationConfig", config));
            HttpRequest request = HttpRequest
                    .newBuilder(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                            + properties.getModel() + ":generateContent"))
                    .timeout(Duration.ofSeconds(timeoutSeconds)).header("Content-Type", "application/json")
                    .header("x-goog-api-key", properties.getApiKey().trim())
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            pending = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> response = pending.get(timeoutSeconds, TimeUnit.SECONDS);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw AiFailure.unavailable();
            }
            if (response.body() == null || response.body().length() > 150_000) {
                throw AiFailure.invalid();
            }
            // Chỉ nhận kết quả hoàn tất; bỏ phần suy nghĩ nội bộ của model.
            JsonNode candidate = json.readTree(response.body()).path("candidates").path(0);
            if (!"STOP".equals(candidate.path("finishReason").asText())) {
                throw AiFailure.invalid();
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode part : candidate.path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false) && part.path("text").isTextual()) {
                    text.append(part.path("text").asText());
                }
            }
            if (text.toString().isBlank()) {
                throw AiFailure.invalid();
            }
            return text.toString().trim();
        } catch (TimeoutException exception) {
            throw AiFailure.timeout();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw AiFailure.unavailable();
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof HttpTimeoutException) {
                throw AiFailure.timeout();
            }
            throw AiFailure.unavailable();
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw AiFailure.invalid();
        } finally {
            if (pending != null && !pending.isDone()) {
                pending.cancel(true);
            }
        }
    }
}
