package com.careerbridge.resume.model;

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

import java.time.LocalDateTime;

/**
 * One generated resume. Each generation is a new row with version incremented -- resumes are
 * immutable once built, so there is no updatedAt.
 *
 * pdfContent is stored in the row (bytea), not on disk. Removes the whole "row exists but the file
 * is missing" failure mode a disk-backed design would need, and survives container restarts and
 * multiple replicas for free -- a named Docker volume does neither once there is more than one
 * replica. Resumes are on the order of 100KB, trivial for Postgres.
 *
 * @Column(columnDefinition = "bytea"), never @Lob: Hibernate's PostgreSQLDialect maps a bare @Lob
 * byte[] to the oid large-object type the same way it maps @Lob String, and the ORM insert path
 * cannot fill an oid column from a plain byte array either.
 *
 * There is no fileUrl column. "/api/resume/download/" + id is derivable in the response mapper
 * once the row has an id, so storing it would only add a second write after the first save just to
 * learn the generated id -- the exact save-then-update-then-save-again shape this design avoids.
 */
@Entity
@Table(name = "student_resumes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentResume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    /** Format: "resume_{studentId}_v{version}.pdf". Display name only; the id is what /download/{id} keys on. */
    @Column(nullable = false)
    private String fileName;

    @Builder.Default
    @Column(nullable = false)
    private Integer version = 1;

    /**
     * 0.00-100.00, matching prs-service's own score fields, which are all Double -- not BigDecimal.
     * Becomes PlacementReadinessScore.resumeScore verbatim once resume.generated is consumed.
     */
    @Column(nullable = false)
    private Double atsScore;

    /** Every prior resume is flipped to false on a new generation; see ResumeServiceImpl. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean isDefault = true;

    @Column(columnDefinition = "bytea", nullable = false)
    private byte[] pdfContent;

    // Builder options and ATS breakdown, snapshotted at generation time -- a resume is immutable
    // once built, so these describe exactly what THIS version was built with/scored against, even
    // if the student's live profile or a later generation's toggles differ.
    //
    // Nullable, added to an already-populated table -- same reasoning as StudentProfile.avatarImage
    // in student-service: a NOT NULL column with no DEFAULT fails ddl-auto's ALTER against existing
    // rows. Every include* toggle is read as "true unless explicitly false" wherever it matters
    // (PDF rendering), so a null on a pre-migration row behaves exactly like the old always-on
    // default.

    @Column(columnDefinition = "TEXT")
    private String summary;

    private Boolean includePhone;
    private Boolean includeEmail;
    private Boolean includeLinks;
    private Boolean includeLocation;

    private Boolean includeExperience;
    private Boolean includeSkills;
    private Boolean includeProjects;
    private Boolean includeEducation;
    private Boolean includeCertificates;

    @Column(columnDefinition = "TEXT")
    private String jobDescription;

    // columnDefinition carries a DB-level DEFAULT, not just @Builder.Default's Java-side one -- a
    // NOT NULL column added to an already-populated table fails ddl-auto's ALTER silently (WARN
    // only) without one. Hit exactly this against live data during this feature's own rollout: the
    // ALTER for this column failed, the column was never created, and every /my and /generate call
    // 500'd on "column is_tailored does not exist" until this fix. Same pattern as prs-service's
    // resumeScore.
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private Boolean isTailored = false;

    private String closestCareerName;

    /** Comma-joined, not a join table -- a handful of short keyword strings, not relational data. */
    @Column(columnDefinition = "TEXT")
    private String matchedKeywords;

    @Column(columnDefinition = "TEXT")
    private String missingKeywords;

    /** Snapshot booleans for the ATS breakdown card: did this version's profile have these sections. */
    private Boolean hasEducation;
    private Boolean hasProjects;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime generatedAt;
}
