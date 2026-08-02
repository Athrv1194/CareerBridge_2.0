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

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime generatedAt;
}
