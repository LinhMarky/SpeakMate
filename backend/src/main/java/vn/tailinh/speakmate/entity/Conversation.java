package vn.tailinh.speakmate.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.tailinh.speakmate.util.constant.*;

@Entity
@Table(name = "conversations")
@Getter
@Setter
public class Conversation extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Column(nullable = false, columnDefinition = "CHAR(36)")
    private String createRequestId;

    // Bản chụp trình độ và chủ đề tại thời điểm bắt đầu bài luyện.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 2)
    private EnglishLevel profileLevelSnapshot;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 2)
    private EnglishLevel level;

    @Column(nullable = false, length = 100)
    private String topicNameSnapshot;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String scenarioSnapshot;

    @Column(columnDefinition = "DATETIME(6)")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.NOT_STARTED;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 2)
    private EnglishLevel recommendedLevel;

    @Column(columnDefinition = "TEXT")
    private String levelFeedback;

    private Byte selfRating;

    // Mã của lần gọi AI hiện tại, dùng để từ chối kết quả đến muộn.
    @Column(columnDefinition = "CHAR(36)")
    private String processingToken;

    @Column(columnDefinition = "DATETIME(6)")
    private Instant processingStartedAt;

    public boolean isOpen() {
        return endedAt == null;
    }

    public void clearProcessing() {
        processingToken = null;
        processingStartedAt = null;
    }
}
