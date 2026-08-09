package com.careerbridge.auth.model;

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
 * A self-service request from an already-registered user to link their account to an organization
 * they didn't specify (or couldn't, not knowing the id yet) at registration time.
 *
 * This is deliberately a NEW table rather than reusing organization-service's OrganizationRequest:
 * that one is a college applying to become a tenant at all (creates a brand-new Organization and
 * provisions its first ORG_ADMIN); this one is an existing user, on an existing organization,
 * asking to be linked to it. Different actors, different targets, different approval authority
 * (SUPER_ADMIN there, the org's own ORG_ADMIN here) -- conflating them would mean one workflow
 * doing two unrelated things depending on who reads it.
 *
 * Lives in auth-service, not organization-service, because the field this whole request exists to
 * change -- User.organizationId -- lives here. organization-service has no reason to know a request
 * like this happened at all.
 */
@Entity
@Table(name = "organization_join_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationJoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long organizationId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JoinRequestStatus status = JoinRequestStatus.PENDING;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime reviewedAt;

    private Long reviewedByUserId;
}
