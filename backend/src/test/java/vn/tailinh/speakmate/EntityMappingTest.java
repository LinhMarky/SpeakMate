package vn.tailinh.speakmate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import vn.tailinh.speakmate.entity.Topic;
import vn.tailinh.speakmate.entity.User;
import vn.tailinh.speakmate.repository.TopicRepository;
import vn.tailinh.speakmate.repository.UserRepository;
import vn.tailinh.speakmate.util.constant.EnglishLevel;
import vn.tailinh.speakmate.util.constant.Role;

@Tag("mysql")
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EntityMappingTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void disposableDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("TEST_DB_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:13317/speakmate_test(?:\\?.*)?")) {
            throw new IllegalStateException(
                    "mysqlTest requires TEST_DB_URL for disposable 127.0.0.1:13317/speakmate_test; see README.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
    }

    @Test
    void userDefaultsAndRefreshHashRoundTripThroughMysql() {
        User user = new User();
        user.setName("Nguyễn An");
        user.setEmail("an@example.test");
        user.setPassword("TEST_ONLY_HASH_NOT_A_REAL_CREDENTIAL");
        user.setRefreshToken("a".repeat(64));
        user.setRefreshExpiresAt(Instant.now().plusSeconds(3600));
        userRepository.saveAndFlush(user);
        entityManager.clear();

        User saved = userRepository.findByEmail("an@example.test").orElseThrow();
        assertThat(saved.getId()).isPositive();
        assertThat(saved.getName()).isEqualTo("Nguyễn An");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getLevel()).isEqualTo(EnglishLevel.A1);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getRefreshToken()).isEqualTo("a".repeat(64));
        assertThat(saved.getRefreshExpiresAt()).isAfter(Instant.now());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo("system");
        assertThat(saved.getUpdatedAt()).isNull();
        assertThat(saved.getUpdatedBy()).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT role FROM users WHERE id = ?", String.class, saved.getId()))
                .isEqualTo("USER");
        assertThat(
                jdbcTemplate.queryForObject("SELECT is_active FROM users WHERE id = ?", Integer.class, saved.getId()))
                .isEqualTo(1);
    }

    @Test
    void topicUpdatePreservesCreationAuditAndUsesUtcMicroseconds() {
        Topic topic = new Topic();
        topic.setName("Nhận phòng khách sạn");
        topic.setDescription("Mô tả tình huống. ".repeat(100));
        topic.setScenario("You are a hotel receptionist. ".repeat(100));
        topicRepository.saveAndFlush(topic);
        entityManager.clear();

        Topic saved = topicRepository.findById(topic.getId()).orElseThrow();
        Instant createdAt = saved.getCreatedAt();
        assertThat(saved.getLevel()).isEqualTo(EnglishLevel.A1);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getUpdatedAt()).isNull();
        assertThat(saved.getDescription()).isEqualTo(topic.getDescription());
        assertThat(saved.getScenario()).isEqualTo(topic.getScenario());

        saved.setLevel(EnglishLevel.B2);
        saved.setActive(false);
        saved.setScenario("You are a receptionist helping a guest change a reservation.");
        topicRepository.saveAndFlush(saved);
        entityManager.clear();

        Topic updated = topicRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getCreatedBy()).isEqualTo("system");
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(createdAt);
        assertThat(updated.getUpdatedBy()).isEqualTo("system");
        assertThat(updated.getLevel()).isEqualTo(EnglishLevel.B2);
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getCreatedAt().getNano() % 1000).isZero();
        assertThat(updated.getUpdatedAt().getNano() % 1000).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+00:00");
    }

    @Test
    void topicQueryExcludesDisabledSituations() {
        Topic visible = new Topic();
        visible.setName("Ordering coffee");
        visible.setScenario("You are a barista.");
        Topic disabled = new Topic();
        disabled.setName("Booking a flight");
        disabled.setScenario("You are a travel agent.");
        disabled.setActive(false);
        topicRepository.saveAllAndFlush(List.of(visible, disabled));
        entityManager.clear();

        assertThat(topicRepository.findAllByActiveTrueOrderByNameAsc()).extracting(Topic::getId)
                .contains(visible.getId()).doesNotContain(disabled.getId());
    }
}
