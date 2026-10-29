package vn.tailinh.speakmate.service;

import java.time.Clock;
import java.util.*;
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
import vn.tailinh.speakmate.util.constant.*;
import vn.tailinh.speakmate.util.mapper.SavedWordMapper;

@Service
@RequiredArgsConstructor
public class SavedWordService {

    private final SavedWordRepository words;

    private final WordSuggestionRepository suggestions;

    private final UserService users;

    private final Clock clock;

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

    private BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
