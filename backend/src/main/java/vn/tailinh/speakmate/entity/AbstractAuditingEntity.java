package vn.tailinh.speakmate.entity;

import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
public abstract class AbstractAuditingEntity {

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;

    @Column(name = "updated_at", columnDefinition = "DATETIME(6)")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    // JPA tự gọi trước INSERT để điền thời điểm tạo.
    @PrePersist
    protected void handleBeforeCreate() {
        this.createdAt = Instant.now();
        this.createdBy = currentActor();
    }

    // Khi cập nhật, giữ nguyên thông tin tạo ban đầu.
    @PreUpdate
    protected void handleBeforeUpdate() {
        this.updatedAt = Instant.now();
        this.updatedBy = currentActor();
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication.getName();
        }
        return "system";
    }
}
