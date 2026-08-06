package com.careerbridge.organization.model;

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

import java.time.LocalDateTime;

/**
 * A public college's application to join CareerBridge as a tenant, reviewed by SUPER_ADMIN.
 *
 * This table is new, unlike Organization -- so unlike Organization.type (kept a plain String
 * specifically to avoid a CHECK-constraint ALTER against populated rows), status here can safely
 * be a real @Enumerated(EnumType.STRING) enum: there are no existing rows for Hibernate's generated
 * CHECK constraint to violate.
 *
 * institutionCode lives here, not on Organization -- nothing downstream of Organization has ever
 * read a "code" field, and adding one to that already-populated table for a value nothing consumes
 * would repeat the exact failure class logged three times already (Question.updatedAt,
 * StudentProfile.role, PlacementReadinessScore.resumeScore).
 */
@Entity
@Table(name = "organization_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String institutionName;

    /** Applicant-facing dedupe key, e.g. "COEP". Unique so a second application can't silently collide. */
    @Column(nullable = false, unique = true)
    private String institutionCode;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String contactPersonName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String contactEmail;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String contactPhone;

    @Column(columnDefinition = "TEXT")
    private String websiteDomain;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(columnDefinition = "TEXT")
    private String city;

    @Column(columnDefinition = "TEXT")
    private String state;

    /** Copied onto Organization.type on approval -- that column is NOT NULL. */
    @Column(nullable = false)
    private String organizationType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    /** Set on approve -- lets a caller trace a request forward to the tenant it created. */
    private Long createdOrganizationId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime reviewedAt;

    private Long reviewedByUserId;
}
