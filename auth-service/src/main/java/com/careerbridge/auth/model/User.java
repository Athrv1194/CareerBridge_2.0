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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;

    private String lastName;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    // STRING, not the JPA default ordinal: reordering the enum would otherwise
    // silently remap every existing row to a different role.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private Long organizationId;

    /**
     * The department within organizationId this user belongs to, e.g. "CS and IT". Assigned by that
     * organization's ORG_ADMIN, never self-set -- see AdminUserService.assignDepartment.
     *
     * Nullable with no @Builder.Default, deliberately: this column is added to an already-populated
     * table, and a NOT NULL column with no DEFAULT makes ddl-auto's ALTER fail against existing rows
     * silently, as a WARN. That failure has cost this project three incidents (Question.updatedAt,
     * StudentProfile.role, PlacementReadinessScore.resumeScore). Null means unassigned, which is the
     * correct state for every pre-existing row and for anyone with no organization at all.
     *
     * Free text rather than a foreign key to organization-service's departments table: that table
     * lives in a different database, so no FK is possible, and a synchronous validation call would
     * be a new failure mode this service does not otherwise have -- the same reasoning already
     * documented on LinkOrganizationRequest.organizationId. The College Dashboard supplies the
     * caller's real department list as a dropdown, so drift is bounded in practice.
     */
    private String department;

    @Builder.Default
    private String subscriptionPlan = "FREE";

    private LocalDateTime subscriptionExpiry;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
