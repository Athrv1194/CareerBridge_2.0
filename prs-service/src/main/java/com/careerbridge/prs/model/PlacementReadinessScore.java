package com.careerbridge.prs.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// One row per student (uk_prs_student_id). Raw inputs stored unweighted so weights can be re-tuned.
// totalScore = assessmentScore×0.40 + roadmapScore×0.30 + profileScore×0.20 + resumeScore×0.10
// uk_prs_student_id is the real idempotency guard for at-least-once RabbitMQ delivery.
@Entity
@Table(
        name = "placement_readiness_scores",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_prs_student_id",
                columnNames = {"student_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlacementReadinessScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    // Harvested from student.registered for tenant-scoped leaderboard. Nullable: SUPER_ADMIN and
    // defensive-fallback rows have no org. Null rows are simply absent from ORG_ADMIN queries.
    @Column(name = "organization_id")
    private Long organizationId;

    /** Raw matchPercentage from recommendation.generated, 0-100. Carries 40% of totalScore. */
    @Builder.Default
    @Column(nullable = false)
    private Double assessmentScore = 0.0;

    /** Raw completionPercentage from roadmap.updated, 0-100. Carries 30% of totalScore. */
    @Builder.Default
    @Column(nullable = false)
    private Double roadmapScore = 0.0;

    // Never written as -1.0 -- that is StudentServiceClient's failure sentinel meaning "keep last known value".
    /** Raw profileCompletionPercentage from student-service, 0-100. Carries 20% of totalScore. */
    @Builder.Default
    @Column(nullable = false)
    private Double profileScore = 0.0;

    // columnDefinition DEFAULT 0 is mandatory: NOT NULL with no DEFAULT makes ddl-auto's ALTER fail
    // silently (WARN only) against pre-existing rows.
    /** Raw ATS score from resume.generated, 0-100. Carries 10% of totalScore. */
    @Builder.Default
    @Column(nullable = false, columnDefinition = "double precision not null default 0")
    private Double resumeScore = 0.0;

    // Tracked but NOT weighted -- the four weights already sum to 1.00, adding a fifth would
    // re-cut them and move every existing score on deploy for an unrelated reason.
    // Written by SET (absolute count × 5.0, capped 100), never incremented -- safe for redelivery.
    /** Mentoring engagement, 0-100. Visible on PrsResponse but excluded from computeTotalScore. */
    @Builder.Default
    @Column(nullable = false, columnDefinition = "double precision not null default 0")
    private Double mentoringScore = 0.0;

    /** Derived, recomputed on every update. Never incremented. */
    @Builder.Default
    @Column(nullable = false)
    private Double totalScore = 0.0;

    // TEXT not @Lob: PostgreSQLDialect maps @Lob String to oid, which rejects plain strings.
    // String not enum: adding a grade band is a data change, not a redeploy.
    @Builder.Default
    @Column(columnDefinition = "TEXT", nullable = false)
    private String grade = "F";

    @UpdateTimestamp
    private LocalDateTime lastUpdatedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
