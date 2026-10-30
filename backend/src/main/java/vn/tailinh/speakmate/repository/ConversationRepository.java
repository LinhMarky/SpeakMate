package vn.tailinh.speakmate.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.tailinh.speakmate.entity.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("select count(c) from Conversation c where c.endedAt is not null")
    long countFinished();

    Optional<Conversation> findByIdAndUserId(Long id, Long userId);

    Optional<Conversation> findByUserIdAndCreateRequestId(Long userId, String requestId);

    Page<Conversation> findByUserId(Long userId, Pageable pageable);

    // Vừa kiểm tra chủ sở hữu vừa khóa dòng để gửi tin/kết thúc không ghi chồng nhau.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id and c.user.id = :userId")
    Optional<Conversation> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
