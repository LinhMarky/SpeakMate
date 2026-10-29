package vn.tailinh.speakmate.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tailinh.speakmate.entity.WordSuggestion;

public interface WordSuggestionRepository extends JpaRepository<WordSuggestion, Long> {

    List<WordSuggestion> findByConversationIdOrderByIdAsc(Long conversationId);
}
