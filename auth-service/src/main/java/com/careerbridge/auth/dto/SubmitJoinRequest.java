package com.careerbridge.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body of POST /api/auth/me/organization-requests. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitJoinRequest {

    @NotNull(message = "organizationId is required")
    private Long organizationId;
}
