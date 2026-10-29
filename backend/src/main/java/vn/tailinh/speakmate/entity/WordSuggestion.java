package vn.tailinh.speakmate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "word_suggestions")
@Getter
@Setter
public class WordSuggestion extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Column(nullable = false, length = 100)
    private String word;

    @Column(nullable = false, length = 255)
    private String meaning;

    @Column(columnDefinition = "TEXT")
    private String example;
}
