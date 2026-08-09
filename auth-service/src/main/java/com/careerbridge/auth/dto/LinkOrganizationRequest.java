package com.careerbridge.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of PATCH /api/auth/admin/users/{userId}/organization.
 *
 * organizationId is deliberately nullable and carries no @NotNull -- passing null is how a
 * SUPER_ADMIN unlinks a user from an organization, the same way PUT /organization/{id} treats a
 * null field as "clear it", not "leave unchanged" (that endpoint's null means "unchanged"; this one
 * has no partial-update ambiguity since it is the only field). Not validated against
 * organization-service: POST /auth/register has never validated this field either, and adding a
 * synchronous cross-service call here would be a new failure mode this service doesn't otherwise
 * have, not a one-line addition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkOrganizationRequest {

    private Long organizationId;
}
