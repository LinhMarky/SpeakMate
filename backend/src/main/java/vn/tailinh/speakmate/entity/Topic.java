package vn.tailinh.speakmate.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.tailinh.speakmate.util.constant.EnglishLevel;

@Entity
@Table(name = "topics")
@Getter
@Setter
public class Topic extends AbstractAuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 2)
    private EnglishLevel level = EnglishLevel.A1;

    // Vai của AI và tình huống luyện nói do admin quản lý.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String scenario;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
