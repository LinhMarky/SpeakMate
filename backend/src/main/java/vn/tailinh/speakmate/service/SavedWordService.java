package vn.tailinh.speakmate.service;

import java.time.Clock;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tailinh.speakmate.dto.request.word.*;
import vn.tailinh.speakmate.dto.response.PageResponse;
import vn.tailinh.speakmate.dto.response.word.*;
import vn.tailinh.speakmate.entity.*;
import vn.tailinh.speakmate.exception.BusinessException;
import vn.tailinh.speakmate.repository.*;
import vn.tailinh.speakmate.service.ai.AiClient;
import vn.tailinh.speakmate.service.ai.AiFailure;
import vn.tailinh.speakmate.util.constant.*;
import vn.tailinh.speakmate.util.mapper.SavedWordMapper;

@Service
@RequiredArgsConstructor
public class SavedWordService {

    private final SavedWordRepository words;

    private final WordSuggestionRepository suggestions;

    private final UserService users;

    private final Clock clock;

    private final AiClient ai;

    private final ObjectMapper json;

    @Transactional(readOnly = true)
    public PageResponse<SavedWordResponse> list(int page, int size, SavedWordStatus status) {
        Long userId = users.getCurrentUser().getId();
        var pageable = PageResponse.request(page, size, Sort.by(Sort.Direction.DESC, "id"));
        var result = status == null ? words.findByUserId(userId, pageable)
                : words.findByUserIdAndStatus(userId, status, pageable);
        return PageResponse.from(result.map(SavedWordMapper::toResponse));
    }

    @Transactional
    public SaveSuggestionsResponse save(SaveSuggestionsRequest request) {
        // Khóa người dùng để hai yêu cầu đồng thời không lưu trùng một từ.
        User user = users.lockCurrentUser();
        if (new HashSet<>(request.suggestionIds()).size() != request.suggestionIds().size()) {
            throw invalid("Danh sách suggestionIds không được trùng");
        }
        // Kiểm tra quyền sở hữu và báo cáo của cả danh sách trước khi lưu từ nào.
        List<WordSuggestion> selected = request.suggestionIds().stream().sorted()
                .map(id -> suggestions.findById(id).orElseThrow(BusinessException::notFound)).toList();
        for (WordSuggestion s : selected) {
            if (!s.getConversation().getUser().getId().equals(user.getId())) {
                throw BusinessException.notFound();
            }
        }
        for (WordSuggestion s : selected) {
            if (s.getConversation().getReviewStatus() != ReviewStatus.COMPLETED) {
                throw BusinessException.conflict("REVIEW_NOT_COMPLETED", "Chỉ lưu từ sau khi báo cáo đã hoàn tất");
            }
        }
        List<SaveSuggestionsResponse.Item> created = new ArrayList<>();
        List<SaveSuggestionsResponse.Item> existing = new ArrayList<>();
        for (WordSuggestion s : selected) {
            SavedWord old = words.findByUserIdAndWord(user.getId(), s.getWord()).orElse(null);
            if (old != null) {
                // Giữ nguyên nghĩa và tiến độ của từ người dùng đã lưu trước đó.
                existing.add(new SaveSuggestionsResponse.Item(s.getId(), SavedWordMapper.toResponse(old)));
            } else {
                SavedWord word = new SavedWord();
                word.setUser(user);
                word.setWord(s.getWord());
                word.setMeaning(s.getMeaning());
                word.setExample(s.getExample());
                words.saveAndFlush(word);
                created.add(new SaveSuggestionsResponse.Item(s.getId(), SavedWordMapper.toResponse(word)));
            }
        }
        return new SaveSuggestionsResponse(created, existing);
    }

    @Transactional
    public SavedWordResponse review(long id, ReviewWordRequest request) {
        User user = users.lockCurrentUser();
        if (request.status() == SavedWordStatus.NEW) {
            throw invalid("Chọn LEARNING hoặc MASTERED sau khi ôn");
        }
        SavedWord word = words.findByIdAndUserId(id, user.getId()).orElseThrow(BusinessException::notFound);
        word.setStatus(request.status());
        word.setLastReviewedAt(clock.instant());
        return SavedWordMapper.toResponse(word);
    }

    public List<GeneratedWordResponse> generate(GenerateVocabularyRequest request) {
        String output = ai.generateVocabulary(
                request.topic().trim(), request.level(), request.count());
        try {
            List<GeneratedWordResponse> parsed = json.readerForListOf(GeneratedWordResponse.class)
                    .readValue(output);
            if (parsed == null || parsed.isEmpty()) {
                throw AiFailure.invalid();
            }
            List<GeneratedWordResponse> validated = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (GeneratedWordResponse w : parsed) {
                if (validated.size() >= request.count()) {
                    break;
                }
                if (w.word() == null || w.word().isBlank()
                        || w.meaning() == null || w.meaning().isBlank()) {
                    continue;
                }
                String trimmedWord = w.word().trim();
                if (trimmedWord.length() > 100) {
                    continue;
                }
                String key = trimmedWord.toLowerCase(Locale.ROOT);
                if (!seen.add(key)) {
                    continue;
                }
                String trimmedMeaning = w.meaning().trim();
                if (trimmedMeaning.length() > 255) {
                    trimmedMeaning = trimmedMeaning.substring(0, 255);
                }
                String trimmedExample = w.example() == null ? null : w.example().trim();
                if (trimmedExample != null && trimmedExample.length() > 1000) {
                    trimmedExample = trimmedExample.substring(0, 1000);
                }
                if (trimmedExample != null && trimmedExample.isEmpty()) {
                    trimmedExample = null;
                }
                validated.add(new GeneratedWordResponse(trimmedWord, trimmedMeaning, trimmedExample));
            }
            if (validated.isEmpty()) {
                throw AiFailure.invalid();
            }
            return validated;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw AiFailure.invalid();
        }
    }

    @Transactional
    public SaveGeneratedWordsResponse saveGenerated(SaveGeneratedWordsRequest request) {
        User user = users.lockCurrentUser();
        Set<String> seen = new HashSet<>();
        for (SaveGeneratedWordsRequest.WordItem item : request.words()) {
            String key = item.word().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                throw invalid("Danh sách từ không được trùng");
            }
        }
        List<SavedWordResponse> created = new ArrayList<>();
        List<SavedWordResponse> existing = new ArrayList<>();
        for (SaveGeneratedWordsRequest.WordItem item : request.words()) {
            String trimmedWord = item.word().trim();
            SavedWord old = words.findByUserIdAndWord(user.getId(), trimmedWord).orElse(null);
            if (old != null) {
                existing.add(SavedWordMapper.toResponse(old));
            } else {
                SavedWord word = new SavedWord();
                word.setUser(user);
                word.setWord(trimmedWord);
                word.setMeaning(item.meaning().trim());
                word.setExample(item.example() == null || item.example().isBlank() ? null : item.example().trim());
                words.saveAndFlush(word);
                created.add(SavedWordMapper.toResponse(word));
            }
        }
        return new SaveGeneratedWordsResponse(created, existing);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
