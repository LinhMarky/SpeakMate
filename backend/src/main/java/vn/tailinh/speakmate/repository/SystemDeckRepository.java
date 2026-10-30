package vn.tailinh.speakmate.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.tailinh.speakmate.entity.SystemDeck;

public interface SystemDeckRepository extends JpaRepository<SystemDeck, Long> {
    Page<SystemDeck> findByActiveTrue(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SystemDeck d where d.id = :id")
    Optional<SystemDeck> findForUpdate(@Param("id") long id);
}
