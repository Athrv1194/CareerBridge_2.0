package com.careerbridge.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * The admin view of a user. Body of every /api/auth/admin/users endpoint.
 *
 * There is deliberately NO password field, hashed or otherwise. User.password holds a BCrypt hash
 * and this DTO is the only thing standing between it and an HTTP response; a hash is still
 * credential material and belongs in no payload. Same reason Option.weight never reaches a client
 * in assessment-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponse {

    private Long id;

    private String firstName;

    private String lastName;

    private String email;

    /**
     * A String, not the Role enum, matching every other DTO and consumer copy in this project. The
     * enum is the internal representation; the wire format stays flat so a new role never becomes a
     * breaking change for a client holding an older copy.
     */
    private String role;

    /** Null for a SUPER_ADMIN, who belongs to no organization. */
    private Long organizationId;

    private String subscriptionPlan;

    /**
     * The soft-delete flag. Always false on a list response (every listing finder filters it), but
     * meaningful on the single-user and activate/deactivate responses, where it is the field the
     * caller just changed.
     */
    private Boolean isDeleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
