package com.careerbridge.assessment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AnswerDto {

    @NotNull(message = "Question is required")
    private Long questionId;

    @NotNull(message = "Selected option is required")
    private Long selectedOptionId;
}
