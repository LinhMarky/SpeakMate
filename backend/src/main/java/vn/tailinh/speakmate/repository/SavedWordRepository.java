package vn.tailinh.speakmate.repository;

import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.tailinh.speakmate.entity.SavedWord;
import vn.tailinh.speakmate.util.constant.SavedWordStatus;

public interface SavedWordRepository extends JpaRepository<SavedWord, Long> {

    Page<SavedWord> findByUserId(Long userId, Pageable pageable);

    Page<SavedWord> findByUserIdAndStatus(Long userId, SavedWordStatus status, Pageable pageable);

    Optional<SavedWord> findByUserIdAndWord(Long userId, String word);

    Optional<SavedWord> findByIdAndUserId(Long id, Long userId);
}
