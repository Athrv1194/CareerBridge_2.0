package com.careerbridge.mentor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A student's review of one completed session. At most one per session, enforced by
 * uq_review_session.
 *
 * The unique constraint is the real guarantee; the existsBySessionId check in createReview is the
 * fast path that produces a friendly 409. Two concurrent submissions race that check and the loser
 * hits the constraint, which is the constraint doing its job.
 */
@Entity
@Table(
        name = "session_reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_review_session",
                columnNames = {"session_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private MentorshipSession session;

    /** Always equal to session.studentId; verified before the row is built. */
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /**
     * Denormalised from session.mentorProfile.id so the average-rating recalculation and the public
     * reviews list are single flat queries rather than a join through mentorship_sessions.
     */
    @Column(name = "mentor_profile_id", nullable = false)
    private Long mentorProfileId;

    /** 1 to 5. Bounded by @Min/@Max on CreateReviewRequest, not by a CHECK constraint. */
    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    /**
     * No @UpdateTimestamp and no updatedAt: a review is written once and never edited. Adding an
     * edit path later means adding both.
     */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
