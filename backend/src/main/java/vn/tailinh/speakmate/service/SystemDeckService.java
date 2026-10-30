package vn.tailinh.speakmate.service;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tailinh.speakmate.dto.request.AdminRequests;
import vn.tailinh.speakmate.dto.response.*;
import vn.tailinh.speakmate.dto.response.word.SavedWordResponse;
import vn.tailinh.speakmate.entity.*;
import vn.tailinh.speakmate.exception.BusinessException;
import vn.tailinh.speakmate.repository.*;
import vn.tailinh.speakmate.util.mapper.SavedWordMapper;

@Service
@RequiredArgsConstructor
public class SystemDeckService {
    private final SystemDeckRepository decks;
    private final SavedWordRepository words;
    private final UserService users;

    @Transactional(readOnly = true)
    public PageResponse<SystemDeckResponse> list(int page, int size, boolean admin) {
        if (admin) AdminService.requireAdmin(users.getCurrentUser());
        var request = PageResponse.request(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.from((admin ? decks.findAll(request) : decks.findByActiveTrue(request))
                .map(SystemDeckResponse::from));
    }

    @Transactional
    public SystemDeckResponse save(long id, AdminRequests.Deck request) {
        AdminService.requireAdmin(users.lockCurrentUser());
        Set<String> seen = new HashSet<>();
        for (var word : request.words()) {
            if (!seen.add(word.word().trim().toLowerCase(Locale.ROOT))) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Các từ trong bộ không được trùng.");
            }
        }
        SystemDeck deck = id > 0 ? decks.findForUpdate(id).orElseThrow(BusinessException::notFound) : new SystemDeck();
        deck.setName(request.name().trim());
        deck.setLevel(request.level());
        deck.getWords().clear();
        for (var word : request.words()) {
            deck.getWords().add(new SystemDeckWord(word.word().trim(), word.meaning().trim(),
                    word.example() == null ? null : word.example().trim()));
        }
        return SystemDeckResponse.from(decks.saveAndFlush(deck));
    }

    @Transactional
    public SystemDeckResponse setActive(long id, boolean active) {
        AdminService.requireAdmin(users.lockCurrentUser());
        SystemDeck deck = decks.findForUpdate(id).orElseThrow(BusinessException::notFound);
        deck.setActive(active);
        return SystemDeckResponse.from(deck);
    }

    @Transactional
    public List<SavedWordResponse> saveForLearner(long id) {
        User user = users.lockCurrentUser();
        SystemDeck deck = decks.findForUpdate(id).orElseThrow(BusinessException::notFound);
        if (!deck.isActive()) throw BusinessException.notFound();
        List<SavedWordResponse> result = new ArrayList<>();
        // Sao chép vào từ cá nhân; admin sửa bộ mẫu không thay tiến độ/nội dung đã lưu.
        for (SystemDeckWord source : deck.getWords()) {
            SavedWord word = words.findByUserIdAndWord(user.getId(), source.getWord()).orElse(null);
            if (word == null) {
                word = new SavedWord();
                word.setUser(user);
                word.setWord(source.getWord());
                word.setMeaning(source.getMeaning());
                word.setExample(source.getExample());
                words.saveAndFlush(word);
            }
            result.add(SavedWordMapper.toResponse(word));
        }
        return result;
    }
}
