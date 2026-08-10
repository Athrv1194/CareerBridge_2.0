package com.careerbridge.mentor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A 1:1 session a student requested with a mentor.
 *
 * Status flow: REQUESTED -> ACCEPTED -> COMPLETED, with DECLINED and CANCELLED as terminal
 * branches. Enforced in MentorServiceImpl, not by the schema.
 *
 * Deliberately NO unique constraint on (student_id, mentor_profile_id): a student may book the same
 * mentor repeatedly over time, and only one ACTIVE session at a time is disallowed. That rule is
 * the existsByStudentIdAndMentorProfileIdAndStatusIn([REQUESTED, ACCEPTED]) check in bookSession --
 * a convention this service maintains rather than something the schema enforces, the same shape as
 * payment-service's one-ACTIVE-subscription-per-user rule and for the same reason: the obvious
 * constraint would break the commonest legitimate case.
 */
@Entity
@Table(name = "mentorship_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorshipSession {

    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_DECLINED = "DECLINED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** From X-User-Id when the student books. */
    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /**
     * Denormalised copy of mentorProfile.userId.
     *
     * Carried alongside the relationship on purpose: every ownership check in this service compares
     * the caller's X-User-Id against this, and reading it off the LAZY mentorProfile would force a
     * proxy initialisation on the authorization path -- the one path that must work identically
     * inside and outside a transaction.
     */
    @Column(name = "mentor_user_id", nullable = false)
    private Long mentorUserId;

    /**
     * LAZY, not the @ManyToOne default of EAGER: the session list endpoints load many rows and only
     * the detail mapper needs the profile. Read paths that map this to a DTO are
     * @Transactional(readOnly = true) so the proxy can initialise -- see MentorServiceImpl.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentor_profile_id", nullable = false)
    private MentorProfile mentorProfile;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String topic;

    /** Proposed by the student at booking time. Not renegotiated -- the mentor accepts or declines. */
    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    @Builder.Default
    @Column(nullable = false)
    private Integer durationMinutes = 30;

    /**
     * A String rather than an @Enumerated enum, matching prs-service's grade column: adding a state
     * stays a code change in one service instead of an ALTER to a generated CHECK constraint on a
     * populated table -- the failure mode logged four times in this project. The five legal values
     * are the STATUS_* constants above, and every write goes through MentorServiceImpl.
     */
    @Builder.Default
    @Column(nullable = false)
    private String status = STATUS_REQUESTED;

    /** Set by the mentor when accepting. Required for an ONLINE session; there is no other kind yet. */
    private String meetingLink;

    /** Optional note from the mentor when accepting or declining. */
    @Column(columnDefinition = "TEXT")
    private String mentorNotes;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(columnDefinition = "timestamp(6) not null default now()")
    private LocalDateTime updatedAt;
}
