package com.careerbridge.recruiter.model;

import com.careerbridge.recruiter.model.enums.JobType;
import com.careerbridge.recruiter.model.enums.WorkMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "recruiter_id", nullable = false)
    private Long recruiterId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    /** Comma-separated, e.g. "Java,Spring Boot,PostgreSQL". Parsed/joined in the service layer. */
    @Column(columnDefinition = "TEXT")
    private String requiredSkills;

    private String location;

    // STRING, not the JPA default ordinal: reordering the enum would otherwise silently remap
    // every existing row to a different value.
    @Enumerated(EnumType.STRING)
    private WorkMode workMode;

    @Enumerated(EnumType.STRING)
    private JobType jobType;

    private BigDecimal salaryMin;

    private BigDecimal salaryMax;

    private LocalDate applicationDeadline;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(columnDefinition = "timestamp(6) not null default now()")
    private LocalDateTime updatedAt;
}
