package com.careerbridge.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of PATCH /api/auth/admin/users/{userId}/department.
 *
 * department is deliberately nullable and carries no @NotBlank -- passing null (or a blank string,
 * which the service normalises to null) is how an admin UNASSIGNS a user from a department. Same
 * shape and same reasoning as LinkOrganizationRequest.organizationId: this endpoint has a single
 * field, so there is no partial-update ambiguity where null could mean "leave unchanged".
 *
 * @Size caps the stored value. Not validated against organization-service's real department list --
 * see User.department for why that is free text rather than a cross-service lookup.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignDepartmentRequest {

    @Size(max = 100, message = "Department must be at most 100 characters")
    private String department;
}
