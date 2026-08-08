package com.careerbridge.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SubmitAnswerRequest {

    @NotNull(message = "Attempt id is required")
    private Long attemptId;

    @NotEmpty(message = "At least one answer is required")
    @Valid
    private List<AnswerDto> answers;
}
