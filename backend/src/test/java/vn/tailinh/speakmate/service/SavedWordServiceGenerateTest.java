package vn.tailinh.speakmate.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.tailinh.speakmate.dto.request.word.*;
import vn.tailinh.speakmate.dto.response.word.*;
import vn.tailinh.speakmate.entity.SavedWord;
import vn.tailinh.speakmate.entity.User;
import vn.tailinh.speakmate.exception.BusinessException;
import vn.tailinh.speakmate.repository.SavedWordRepository;
import vn.tailinh.speakmate.repository.WordSuggestionRepository;
import vn.tailinh.speakmate.service.ai.AiClient;

class SavedWordServiceGenerateTest {

    private SavedWordRepository words;
    private WordSuggestionRepository suggestions;
    private UserService users;
    private Clock clock;
    private AiClient ai;
    private ObjectMapper json;
    private SavedWordService service;

    private User testUser;

    @BeforeEach
    void setUp() {
        words = mock(SavedWordRepository.class);
        suggestions = mock(WordSuggestionRepository.class);
        users = mock(UserService.class);
        clock = mock(Clock.class);
        ai = mock(AiClient.class);
        json = new ObjectMapper();

        when(clock.instant()).thenReturn(Instant.parse("2026-10-30T00:00:00Z"));

        testUser = new User();
        testUser.setId(10L);
        when(users.getCurrentUser()).thenReturn(testUser);
        when(users.lockCurrentUser()).thenReturn(testUser);

        service = new SavedWordService(words, suggestions, users, clock, ai, json);
    }

    @Test
    void generate_validOutput_returnsValidatedList() {
        String mockAiOutput = """
                [
                    {"word": " luggage ", "meaning": "Hành lý", "example": "Pack your luggage carefully."},
                    {"word": "Ticket", "meaning": "Vé", "example": "Show your ticket at the counter."},
                    {"word": "LUGGAGE", "meaning": "Hành lý lặp", "example": "Duplicate should be ignored."}
                ]
                """;
        when(ai.generateVocabulary("Du lịch", "A2", 5)).thenReturn(mockAiOutput);

        var request = new GenerateVocabularyRequest("Du lịch", "A2", 5);
        List<GeneratedWordResponse> result = service.generate(request);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).word()).isEqualTo("luggage");
        assertThat(result.get(0).meaning()).isEqualTo("Hành lý");
        assertThat(result.get(0).example()).isEqualTo("Pack your luggage carefully.");
        assertThat(result.get(1).word()).isEqualTo("Ticket");
    }

    @Test
    void generate_invalidJson_throwsBusinessException() {
        when(ai.generateVocabulary("Du lịch", "A2", 5)).thenReturn("Not a json");

        var request = new GenerateVocabularyRequest("Du lịch", "A2", 5);
        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không hợp lệ");
    }

    @Test
    void generate_emptyList_throwsBusinessException() {
        when(ai.generateVocabulary("Du lịch", "A2", 5)).thenReturn("[]");

        var request = new GenerateVocabularyRequest("Du lịch", "A2", 5);
        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không hợp lệ");
    }

    @Test
    void saveGenerated_duplicateWordsInRequest_throwsValidationError() {
        var items = List.of(
                new SaveGeneratedWordsRequest.WordItem("Hotel", "Khách sạn", "We booked a hotel."),
                new SaveGeneratedWordsRequest.WordItem("hotel", "Khách sạn khác", "Another hotel.")
        );
        var request = new SaveGeneratedWordsRequest(items);

        assertThatThrownBy(() -> service.saveGenerated(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Danh sách từ không được trùng");
    }

    @Test
    void saveGenerated_createsNewAndIdentifiesExisting() {
        SavedWord existingWord = new SavedWord();
        existingWord.setId(101L);
        existingWord.setUser(testUser);
        existingWord.setWord("Airport");
        existingWord.setMeaning("Sân bay");
        existingWord.setExample("See you at the airport.");

        when(words.findByUserIdAndWord(10L, "Airport")).thenReturn(Optional.of(existingWord));
        when(words.findByUserIdAndWord(10L, "Passport")).thenReturn(Optional.empty());

        when(words.saveAndFlush(any(SavedWord.class))).thenAnswer(invocation -> {
            SavedWord saved = invocation.getArgument(0);
            saved.setId(102L);
            return saved;
        });

        var items = List.of(
                new SaveGeneratedWordsRequest.WordItem("Airport", "Sân bay", "See you at the airport."),
                new SaveGeneratedWordsRequest.WordItem("Passport", "Hộ chiếu", "Keep your passport safe.")
        );
        var request = new SaveGeneratedWordsRequest(items);

        SaveGeneratedWordsResponse response = service.saveGenerated(request);

        assertThat(response.created()).hasSize(1);
        assertThat(response.created().get(0).word()).isEqualTo("Passport");
        assertThat(response.created().get(0).id()).isEqualTo(102L);

        assertThat(response.existing()).hasSize(1);
        assertThat(response.existing().get(0).word()).isEqualTo("Airport");
        assertThat(response.existing().get(0).id()).isEqualTo(101L);

        verify(words, times(1)).saveAndFlush(any(SavedWord.class));
    }
}

