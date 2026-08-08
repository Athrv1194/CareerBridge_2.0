package com.careerbridge.assessment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "assessment_attempts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The auth-service user, arriving via the gateway-forwarded X-User-Id header. */
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long categoryId;

    /**
     * Which fixed section (AssessmentSection name) this attempt was started for -- e.g. "APTITUDE".
     * Nullable: adding a NOT NULL column to a populated table without a DEFAULT fails ddl-auto's
     * ALTER silently (the Question.updatedAt incident, twice more since). Needed because categoryId
     * alone no longer tells you the section's target size or display name once Domain Knowledge can
     * be backed by any of 3 different categories -- see AssessmentConstants.SECTION_CATEGORY_POOL.
     */
    private String section;

    // STRING, not the JPA default ordinal: reordering AttemptStatus would otherwise silently
    // remap every existing row to a different status.
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime startedAt;

    /** Set explicitly when the attempt is submitted; null while IN_PROGRESS. */
    private LocalDateTime completedAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
