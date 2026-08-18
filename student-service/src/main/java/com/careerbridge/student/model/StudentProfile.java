package com.careerbridge.student.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "student_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The auth-service user this profile belongs to. Unique is load-bearing, not decorative:
     * it is the actual idempotency guarantee for the registration consumer, which can receive
     * the same event twice and would otherwise create a duplicate profile on the race.
     */
    @Column(unique = true, nullable = false)
    private Long userId;

    private String firstName;

    private String lastName;

    private String email;

    /**
     * The auth-service role this profile belongs to, harvested from StudentRegisteredEvent.
     *
     * Nullable and with no @Builder.Default on purpose: this column was added to an already
     * populated table, and a NOT NULL column with no DEFAULT makes ddl-auto's ALTER fail against
     * existing rows (silently, as a WARN -- assessment-service's logged Question.updatedAt
     * incident). Rows predating this column were backfilled by hand from careerbridge_auth.users.
     *
     * A String, not auth-service's Role enum, matching StudentRegisteredEvent's own field: a
     * duplicated enum would hard-fail every event the day auth-service adds a seventh role.
     *
     * Exists so getPublicProfiles can return only STUDENT profiles. auth-service publishes
     * student.registered for EVERY registration regardless of role, so without this filter the
     * recruiter candidate pool contains recruiters and admins too.
     */
    private String role;

    /**
     * Local copy of the department auth-service holds on User, kept current by the
     * user.department.updated consumer. Exists so the public candidate profile can carry it --
     * recruiter-service filters on it, and cannot read auth-service synchronously (that service is
     * the only one with Spring Security, and answers a header-only call 401).
     *
     * Nullable with no @Builder.Default, deliberately: this column is added to an already-populated
     * table, and a NOT NULL column with no DEFAULT makes ddl-auto's ALTER fail against existing
     * rows silently, as a WARN. Same rule as `role` above, and the same failure that has cost this
     * project three incidents. Null means unassigned, which is correct for every pre-existing row.
     *
     * A denormalised copy, so it lags auth-service by broker delivery and can go stale if an event
     * is lost -- auth-service's users.department stays the source of truth.
     */
    private String department;

    private String phone;

    // Free text well past the 255-char column default. TEXT, not @Lob: Hibernate's
    // PostgreSQLDialect maps @Lob String to oid (large object), which the ORM insert path
    // cannot fill from a plain String.
    @Column(columnDefinition = "TEXT")
    private String bio;

    private String city;

    private String state;

    private String country;

    private String linkedinUrl;

    private String githubUrl;

    private String portfolioUrl;

    private String resumeUrl;

    // Nullable, no @Builder.Default: same reason as `role` above -- a NOT NULL column with no
    // DEFAULT fails ddl-auto's ALTER against the already-populated table. bytea, not @Lob: see
    // resume-service's student_resumes.pdf_content, the same reasoning applies to any binary
    // column under Hibernate's PostgreSQLDialect. Does not affect profileCompletionPercentage --
    // ProfileCompletionCalculator has no avatar criterion.
    @Column(columnDefinition = "bytea")
    private byte[] avatarImage;

    private String avatarContentType;

    @Builder.Default
    @Column(nullable = false)
    private Integer profileCompletionPercentage = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isPublic = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
