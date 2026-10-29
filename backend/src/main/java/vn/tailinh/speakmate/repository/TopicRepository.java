package vn.tailinh.speakmate.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.tailinh.speakmate.entity.Topic;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    Page<Topic> findByActiveTrue(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Topic t where t.id = :id")
    Optional<Topic> findByIdForUpdate(@Param("id") Long id);

    List<Topic> findAllByActiveTrueOrderByNameAsc();
}
