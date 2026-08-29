package com.careerbridge.recruiter.model;

import com.careerbridge.recruiter.model.enums.ApplicationStatus;
import com.careerbridge.recruiter.model.enums.OfferOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * The unique constraint on (job_id, student_id) is the real duplicate-application guarantee, not
 * the existsByJobIdAndStudentId check in ApplicationServiceImpl -- that check is a TOCTOU fast path.
 *
 * Offer data lives HERE rather than on Interview, deliberately. An offer is the outcome of an
 * application, not of one interview round: interviews.application_id carries no unique constraint
 * because multiple rounds per application are supported on purpose, so "which round holds the
 * offer" has no answer -- three rounds could each carry a different value. On this entity
 * uk_application_job_student already guarantees one application per (job, student), and therefore
 * exactly one offer per application. ApplicationStatus.OFFERED also already lives here, so putting
 * the CTC anywhere else would split one concept across two tables that could then disagree.
 */
@Entity
@Table(
        name = "job_applications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_application_job_student",
                columnNames = {"job_id", "student_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    /**
     * The offered compensation in LPA (lakhs per annum), e.g. 8.50 means Rs 8,50,000 a year.
     *
     * Set when a RECRUITER extends the offer, null until then. Bare BigDecimal with no
     * precision/scale, matching Job.salaryMin/salaryMax and every other BigDecimal in this
     * repository -- Hibernate maps it to numeric(38,2) on PostgreSQL, which is exact.
     *
     * NOTE: Job.salaryMin/salaryMax document no unit anywhere, so this field is establishing the
     * LPA convention rather than following one. Anything rendering both together needs to
     * reconcile them first.
     */
    @Column(name = "offered_ctc")
    private BigDecimal offeredCtc;

    /** When the offer was extended. Null until a recruiter extends one. */
    @Column(name = "offer_date")
    private LocalDateTime offerDate;

    /**
     * The student's decision. Null means no response yet -- which is why there is no PENDING value
     * and no @Builder.Default: a nullable column needs no DEFAULT and so no risky ALTER against
     * this already-populated table. Terminal once set; see respondToOffer.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "offer_outcome")
    private OfferOutcome offerOutcome;

    // Nullable, added to an already-populated table -- same reasoning as Certificate.credentialFile
    // in student-service. A student may attach a résumé tailored to this specific application,
    // separate from their generated CareerBridge résumé.
    @Column(columnDefinition = "bytea")
    private byte[] resumeFile;

    private String resumeFileName;

    private String resumeFileContentType;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime appliedAt;

    @UpdateTimestamp
    @Column(columnDefinition = "timestamp(6) not null default now()")
    private LocalDateTime updatedAt;
}
