package vn.tailinh.speakmate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.tailinh.speakmate.util.constant.MessageSender;

@Entity
@Table(name = "messages")
@Getter
@Setter
public class Message extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private MessageSender sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "CHAR(36)")
    private String requestId;

    @Column(columnDefinition = "TEXT")
    private String correctedContent;

    @Column(columnDefinition = "TEXT")
    private String feedback;
}
