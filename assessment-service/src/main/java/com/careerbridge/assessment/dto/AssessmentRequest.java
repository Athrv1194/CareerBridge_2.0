package com.careerbridge.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** section is an AssessmentSection name, e.g. "APTITUDE" -- the client requests a fixed section, never a raw category. */
@Data
public class AssessmentRequest {

    @NotBlank(message = "Section is required")
    private String section;
}
