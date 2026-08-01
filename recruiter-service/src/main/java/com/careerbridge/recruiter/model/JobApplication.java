package com.careerbridge.recruiter.model;

import com.careerbridge.recruiter.model.enums.ApplicationStatus;
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

import java.time.LocalDateTime;

/**
 * The unique constraint on (job_id, student_id) is the real duplicate-application guarantee, not
 * the existsByJobIdAndStudentId check in ApplicationServiceImpl -- that check is a TOCTOU fast path.
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

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime appliedAt;

    @UpdateTimestamp
    @Column(columnDefinition = "timestamp(6) not null default now()")
    private LocalDateTime updatedAt;
}
