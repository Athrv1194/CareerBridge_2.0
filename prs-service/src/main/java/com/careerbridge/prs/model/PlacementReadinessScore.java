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
 * The four raw inputs are stored as they arrived (0-100 each), never pre-weighted, so the weights
 * can be re-tuned later without the stored values becoming meaningless. totalScore is the derived
 * figure and is always recomputed from all four, never incremented:
 *
 *   totalScore = assessmentScore x 0.40 + roadmapScore x 0.30 + profileScore x 0.20 + resumeScore x 0.10
 *
 * The weights now sum to 1.00. Until 2026-08-01 they summed to 0.90, with the remaining 10%
 * explicitly reserved for "a future resume builder" -- resume-service is that builder, and
 * resumeScore is fed by its resume.generated event, consumed on careerbridge.prs.resume.queue. A
 * student who has never generated a resume keeps resumeScore at its default 0.0, so nothing about
 * an existing score changes on its own; totalScore only rises once a resume actually exists.
 *
 * mentoringScore is a FIFTH stored input that is deliberately NOT part of that formula -- see its
 * own javadoc below. "The four raw inputs" above means the four weighted ones.
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

    /**
     * Raw ATS score (0-100) from the student's latest resume, harvested from resume.generated.
     * Carries 10% of the total. Added 2026-08-01 to a table that already had rows.
     *
     * The DEFAULT 0 in columnDefinition is mandatory, not decorative: a NOT NULL column with no
     * DEFAULT makes ddl-auto's ALTER fail against existing rows, silently, as a WARN -- the
     * Question.updatedAt incident, and StudentProfile.role after it. With the default present,
     * ddl-auto backfills every pre-existing row to 0.0 rather than failing the ALTER.
     */
    @Builder.Default
    @Column(nullable = false, columnDefinition = "double precision not null default 0")
    private Double resumeScore = 0.0;

    /**
     * Mentoring engagement, 0-100: 5.0 per completed mentorship session, capped at 100. Fed by
     * mentor-service's session.completed on careerbridge.prs.mentor.queue.
     *
     * TRACKED BUT NOT WEIGHTED, deliberately. The four weights above already sum to 1.00, so giving
     * mentoring a share would mean re-cutting them, and that would move every existing student's
     * totalScore the moment this deployed -- for a reason unrelated to anything they did. This field
     * is surfaced on PrsResponse so the number is visible and the mentor-service event chain is
     * genuinely proven end to end, while computeTotalScore stays a four-input calculation. Whether
     * mentoring should earn a weight is an open product decision, not an oversight; see
     * PrsServiceImpl.computeTotalScore.
     *
     * Because it is unweighted it is also absent from PrsBreakdown, whose stated contract is that
     * its contributions sum to totalScore exactly.
     *
     * Written by SETTING an absolute value derived from a count carried on the event, never by
     * incrementing -- see PrsServiceImpl.updateMentoringScore.
     *
     * The DEFAULT 0 in columnDefinition is mandatory for the same reason as resumeScore above: this
     * column is being added to a table that already has rows.
     */
    @Builder.Default
    @Column(nullable = false, columnDefinition = "double precision not null default 0")
    private Double mentoringScore = 0.0;

    /** Derived, recomputed on every update. 0-100 since the resume slot activated. */
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
