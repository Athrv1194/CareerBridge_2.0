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

/**
 * One student's Placement Readiness Score. Exactly one row per student, enforced by
 * uk_prs_student_id.
 *
 * The three raw inputs are stored as they arrived (0-100 each), never pre-weighted, so the weights
 * can be re-tuned later without the stored values becoming meaningless. totalScore is the derived
 * figure and is always recomputed from the three, never incremented:
 *
 *   totalScore = assessmentScore x 0.40 + roadmapScore x 0.30 + profileScore x 0.20
 *
 * Note the weights sum to 0.90, not 1.00 -- the remaining 10% is reserved for the future resume
 * builder. totalScore therefore tops out at 90 by design, and PrsBreakdown surfaces the reserved
 * slice explicitly so the gap is self-explaining. Do not "fix" this by normalising to 100: every
 * stored score would silently shift the day the resume builder lands.
 *
 * The unique constraint is the real idempotency guarantee for the student.registered consumer, not
 * the existsByStudentId check in initialisePrs -- RabbitMQ is at-least-once, so a redelivery can
 * race that check and the loser raises DataIntegrityViolationException, which the consumer's
 * fail-soft catch discards. Same design as student_profiles.userId and
 * student_roadmaps(student_id, recommendation_id).
 */
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

    /**
     * Harvested from StudentRegisteredEvent so the leaderboard can be scoped per tenant: an
     * ORG_ADMIN sees only their own college's students, matching organization-service's refusal to
     * serve cross-tenant lists.
     *
     * Deliberately nullable. A SUPER_ADMIN has no organization, and a row created by the defensive
     * fallback path in updateAssessmentScore/updateRoadmapScore has no organization either until a
     * student.registered arrives. Such rows simply do not match an ORG_ADMIN's scoped query, which
     * is the correct outcome -- better to omit a student from one college's leaderboard than to
     * leak them onto another's.
     */
    @Column(name = "organization_id")
    private Long organizationId;

    /** Raw matchPercentage from recommendation.generated, 0-100. Carries 40% of the total. */
    @Builder.Default
    @Column(nullable = false)
    private Double assessmentScore = 0.0;

    /** Raw completionPercentage from roadmap.updated, 0-100. Carries 30% of the total. */
    @Builder.Default
    @Column(nullable = false)
    private Double roadmapScore = 0.0;

    /**
     * Raw profileCompletionPercentage fetched synchronously from student-service, 0-100. Carries
     * 20% of the total. Never written as -1.0: that is StudentServiceClient's failure sentinel and
     * means "keep the last known value", not "the student scored minus one".
     */
    @Builder.Default
    @Column(nullable = false)
    private Double profileScore = 0.0;

    /** Derived, recomputed on every update. 0-90 in practice; see the class comment. */
    @Builder.Default
    @Column(nullable = false)
    private Double totalScore = 0.0;

    /**
     * A / B / C / D / F, derived from totalScore. TEXT, not @Lob: Hibernate's PostgreSQLDialect
     * maps @Lob String to the oid large-object type, which rejects a plain string from both the ORM
     * insert path and any seed SQL. Logged SEV-2.
     *
     * A String rather than an enum so adding a band is a data change rather than a redeploy of this
     * service plus every consumer of prs.updated.
     */
    @Builder.Default
    @Column(columnDefinition = "TEXT", nullable = false)
    private String grade = "F";

    @UpdateTimestamp
    private LocalDateTime lastUpdatedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
