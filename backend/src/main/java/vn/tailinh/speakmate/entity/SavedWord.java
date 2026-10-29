package vn.tailinh.speakmate.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.tailinh.speakmate.util.constant.SavedWordStatus;

@Entity
@Table(name = "saved_words")
@Getter
@Setter
public class SavedWord extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String word;

    @Column(nullable = false, length = 255)
    private String meaning;

    @Column(columnDefinition = "TEXT")
    private String example;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SavedWordStatus status = SavedWordStatus.NEW;

    @Column(columnDefinition = "DATETIME(6)")
    private Instant lastReviewedAt;
}
