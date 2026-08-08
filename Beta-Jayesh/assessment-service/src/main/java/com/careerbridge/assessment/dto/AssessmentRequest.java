package com.careerbridge.assessment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssessmentRequest {

    @NotNull(message = "Category is required")
    private Long categoryId;
}
