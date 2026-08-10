package com.careerbridge.mentor.model;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One mentor's public profile. Exactly one row per MENTOR user, enforced by uk_mentor_profile_user.
 *
 * The unique constraint is the real one-profile-per-mentor guarantee, not the existsByUserId check
 * in createProfile -- that check is only the fast path that produces a friendly 409, and two
 * concurrent requests can race it. Same pattern as student_profiles.userId and
 * placement_readiness_scores.student_id.
 *
 * expertiseAreas and careerPaths are comma-delimited TEXT rather than @ElementCollection. A
 * collection table would add a join for what is only ever read whole and rendered as chips, and the
 * split happens once in the response mapper. Consequence worth knowing: the browse filters use a
 * LIKE against this column, so an expertise of "Java" also matches "JavaScript" -- acceptable for a
 * discovery filter, and the alternative is a normalised table this service has no other use for.
 */
@Entity
@Table(
        name = "mentor_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mentor_profile_user",
                columnNames = {"user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The MENTOR's user id, from the gateway-injected X-User-Id. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    /**
     * TEXT, not @Lob: Hibernate's PostgreSQLDialect maps @Lob String to the oid large-object type,
     * which rejects a plain string from the ORM insert path. Logged SEV-2 on assessment-service.
     */
    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(nullable = false)
    private String currentCompany;

    /**
     * Job title, e.g. "Senior Software Engineer" -- not a CareerBridge Role.
     *
     * The column name MUST stay explicitly quoted. CURRENT_ROLE is a reserved word in PostgreSQL (a
     * niladic function, like CURRENT_USER), and hibernate.auto_quote_keyword is off project-wide, so
     * an unquoted current_role makes ddl-auto emit `... current_role varchar(255) not null ...` and
     * PostgreSQL answers "syntax error at or near current_role".
     *
     * That failure is logged as a WARN and nothing else: the application starts, contextLoads goes
     * green, and mentor_profiles simply does not exist -- along with its unique constraint and the
     * FK from mentorship_sessions. Found exactly that way here on 2026-08-10, and it is the same
     * trap recommendation-service hit with CareerRanking.rank.
     */
    @Column(name = "\"current_role\"", nullable = false)
    private String currentRole;

    @Column(nullable = false)
    private Integer yearsOfExperience;

    /** Comma-separated, e.g. "Java,Spring Boot,System Design". */
    @Column(columnDefinition = "TEXT")
    private String expertiseAreas;

    /**
     * Comma-separated career paths this mentor covers. Intended to line up with the seven names in
     * recommendation-service's CareerCatalog and roadmap-service's RoadmapDataSeeder, but this is a
     * free-text field a mentor types -- deliberately NOT validated against that list, since a
     * mismatch here only narrows one browse filter rather than breaking anything, and a fifth copy
     * of the career list is a maintenance cost this service does not need to carry.
     */
    @Column(columnDefinition = "TEXT")
    private String careerPaths;

    private String linkedinUrl;

    /** A mentor toggles this off when they are too busy; browse only returns available mentors. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean isAvailable = true;

    /** Incremented by completeSession, never decremented -- a completed session stays completed. */
    @Builder.Default
    @Column(nullable = false)
    private Integer sessionsCompleted = 0;

    /**
     * Mean of every review for this profile, recalculated from all rows on each new review rather
     * than maintained as a running average, so it cannot drift.
     *
     * precision 3 / scale 2 fits 0.00 to 9.99, and ratings are 1-5, so the ceiling is 5.00.
     * BigDecimal rather than Double because this is a displayed figure with a fixed 2-decimal
     * contract; the raw 0-100 scores in prs-service are Doubles for a different reason (they are
     * arithmetic inputs, not presentation values).
     */
    @Builder.Default
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(columnDefinition = "timestamp(6) not null default now()")
    private LocalDateTime updatedAt;
}
