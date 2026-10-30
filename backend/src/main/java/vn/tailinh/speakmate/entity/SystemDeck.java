package vn.tailinh.speakmate.entity;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.tailinh.speakmate.util.constant.EnglishLevel;

@Entity
@Table(name = "system_decks")
@Getter
@Setter
public class SystemDeck extends AbstractAuditingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 2)
    private EnglishLevel level;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ElementCollection
    @CollectionTable(name = "system_deck_words", joinColumns = @JoinColumn(name = "deck_id"))
    @OrderColumn(name = "position")
    private List<SystemDeckWord> words = new ArrayList<>();
}
