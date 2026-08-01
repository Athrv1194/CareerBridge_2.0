package com.careerbridge.recruiter.model;

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
 * One row per recruiter. The unique constraint on recruiter_id is the real one-company-per-recruiter
 * guarantee, not the existsByRecruiterId check in CompanyServiceImpl -- that check is a TOCTOU fast
 * path, same pattern as student_profiles.userId and placement_readiness_scores.student_id.
 */
@Entity
@Table(
        name = "companies",
        uniqueConstraints = @UniqueConstraint(name = "uk_company_recruiter_id", columnNames = {"recruiter_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recruiter_id", nullable = false)
    private Long recruiterId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String industry;

    private String website;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String logoUrl;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(columnDefinition = "timestamp(6) not null default now()")
    private LocalDateTime updatedAt;
}
