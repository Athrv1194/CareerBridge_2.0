package com.careerbridge.organization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectOrgRequest {

    @NotBlank(message = "A rejection reason is required")
    private String reason;
}
