package vn.tailinh.speakmate.service.ai;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.net.http.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.tailinh.speakmate.config.AiProperties;
import vn.tailinh.speakmate.dto.response.conversation.MessageResponse;
import vn.tailinh.speakmate.exception.BusinessException;
import vn.tailinh.speakmate.util.constant.*;

class GeminiAiClientTest {

    final HttpClient http = mock(HttpClient.class);

    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    final AiProperties properties = new AiProperties();

    final GeminiAiClient client = new GeminiAiClient(properties, json, new PromptBuilder(), http);

    final AiConversation input = new AiConversation("Coffee", "You are a barista", EnglishLevel.A1, EnglishLevel.B2,
            List.of(new MessageResponse(1L, MessageSender.USER, "A coffee please", "id", Instant.now())));

    @Test
    void missingKeyDoesNotCallNetworkOrProduceDemoAnswer() {
        assertThatThrownBy(() -> client.reply(input)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo("AI_NOT_CONFIGURED"));
        verifyNoInteractions(http);
    }

    @Test
    void keyUsesHeaderAndOnlyFinalTextIsReturned() {
        properties.setApiKey("test-only-not-a-real-key");
        response(200, """
                {"candidates":[{"finishReason":"STOP","content":{"parts":[
                {"thought":true,"text":"Internal reasoning"},{"text":"Sure! What size?"}]}}]}
                """);
        assertThat(client.reply(input)).isEqualTo("Sure! What size?");
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().toString()).isEqualTo(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent");
        assertThat(request.getValue().uri().getQuery()).isNull();
        assertThat(request.getValue().headers().firstValue("x-goog-api-key")).contains("test-only-not-a-real-key");
    }

    @Test
    void truncationAndProviderFailureNeverLeakRawOutput() {
        properties.setApiKey("test-only");
        response(200,
                "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"text\":\"partial\"}]}}]}");
        assertThatThrownBy(() -> client.reply(input)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo("AI_INVALID_RESPONSE"));
        response(429, "provider-secret-error");
        assertThatThrownBy(() -> client.reply(input)).isInstanceOfSatisfying(BusinessException.class, error -> {
            assertThat(error.getCode()).isEqualTo("AI_UNAVAILABLE");
            assertThat(error.getMessage()).doesNotContain("provider-secret-error");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void waitingTimeoutCancelsPendingRequestAndReturnsAiTimeout() throws Exception {
        properties.setApiKey("test-only");
        CompletableFuture<HttpResponse<String>> pending = mock(CompletableFuture.class);
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(pending);
        when(pending.get(anyLong(), eq(TimeUnit.SECONDS))).thenThrow(new TimeoutException("upstream detail"));

        assertThatThrownBy(() -> client.reply(input)).isInstanceOfSatisfying(BusinessException.class, error -> {
            assertThat(error.getCode()).isEqualTo("AI_TIMEOUT");
            assertThat(error.getMessage()).doesNotContain("upstream detail");
        });
        verify(pending).cancel(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void httpTimeoutReturnsAiTimeoutWithoutProviderDetails() {
        properties.setApiKey("test-only");
        CompletableFuture<HttpResponse<String>> pending = CompletableFuture
                .failedFuture(new HttpTimeoutException("upstream detail"));
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(pending);

        assertThatThrownBy(() -> client.reply(input)).isInstanceOfSatisfying(BusinessException.class, error -> {
            assertThat(error.getCode()).isEqualTo("AI_TIMEOUT");
            assertThat(error.getMessage()).doesNotContain("upstream detail");
        });
    }

    @Test
    void reviewParsesStructuredOutputAndRejectsUnknownFields() throws Exception {
        properties.setApiKey("test-only");
        String review = json.writeValueAsString(
                new AiReview(true, "Too little evidence", null, null, null, null, List.of(), List.of()));
        response(200, candidate(review));
        assertThat(client.review(input).insufficientData()).isTrue();
        response(200, candidate(review.substring(0, review.length() - 1) + ",\"extra\":1}"));
        assertThatThrownBy(() -> client.review(input)).isInstanceOf(BusinessException.class);
    }

    private String candidate(String output) throws Exception {
        return json.writeValueAsString(java.util.Map.of("candidates", List.of(java.util.Map.of("finishReason", "STOP",
                "content", java.util.Map.of("parts", List.of(java.util.Map.of("text", output)))))));
    }

    @SuppressWarnings("unchecked")
    private void response(int code, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(code);
        when(response.body()).thenReturn(body);
        when(http.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
    }
}
