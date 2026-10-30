package vn.tailinh.speakmate.dto.response;

import java.util.List;
import vn.tailinh.speakmate.dto.request.AdminRequests.Word;
import vn.tailinh.speakmate.entity.SystemDeck;
import vn.tailinh.speakmate.util.constant.EnglishLevel;

public record SystemDeckResponse(Long id, String name, EnglishLevel level, boolean active, List<Word> words) {
    public static SystemDeckResponse from(SystemDeck deck) {
        return new SystemDeckResponse(deck.getId(), deck.getName(), deck.getLevel(), deck.isActive(),
                deck.getWords().stream().map(w -> new Word(w.getWord(), w.getMeaning(), w.getExample())).toList());
    }
}
