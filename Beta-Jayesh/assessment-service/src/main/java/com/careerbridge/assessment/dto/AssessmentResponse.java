package com.careerbridge.assessment.dto;

import com.careerbridge.assessment.model.AttemptStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Body of POST /api/assessment/attempt/start. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentResponse {

    private Long attemptId;

    private Long categoryId;

    private String categoryName;

    private AttemptStatus status;

    private LocalDateTime startedAt;

    private List<QuestionDto> questions;
}
