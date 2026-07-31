package com.careerbridge.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of PATCH /api/auth/admin/users/{userId}/role.
 *
 * role is a String rather than the Role enum on purpose. Binding straight to the enum would make
 * Jackson reject an unknown value with HttpMessageNotReadableException, which GlobalExceptionHandler
 * reports as a generic "Malformed JSON request" 400 -- accurate but useless to the caller, who
 * cannot tell a typo'd role from a broken body. AdminUserServiceImpl validates the string against
 * Role.valueOf and throws a 400 naming the offending value instead.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRoleRequest {

    @NotBlank(message = "Role is required")
    private String role;
}
