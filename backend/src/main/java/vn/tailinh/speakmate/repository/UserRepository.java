package vn.tailinh.speakmate.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import vn.tailinh.speakmate.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where (:active is null or u.active = :active) "
            + "and (lower(u.name) like lower(concat('%', :search, '%')) "
            + "or lower(u.email) like lower(concat('%', :search, '%')))")
    org.springframework.data.domain.Page<User> searchAdmin(@Param("search") String search,
            @Param("active") Boolean active, org.springframework.data.domain.Pageable pageable);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleSubject(String googleSubject);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.googleSubject = :subject")
    Optional<User> findByGoogleSubjectForUpdate(@Param("subject") String subject);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);
}
