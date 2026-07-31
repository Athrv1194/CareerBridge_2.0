package com.careerbridge.assessment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminOptionRequest {

    @NotBlank(message = "Option text is required")
    private String text;

    /**
     * Graded 0..3, matching Option.weight and AssessmentConstants.MAX_OPTION_WEIGHT -- NOT a binary
     * correct/incorrect flag.
     *
     * The scoring model is graded on purpose: maxPossibleScore is a fixed
     * QUESTIONS_PER_ATTEMPT x MAX_OPTION_WEIGHT (15), and the seeded bank uses the full range for
     * partial credit (best answer 3, partial 2 and 1, wrong 0). An isCorrect flag would collapse
     * that to 3-or-0 for admin-created questions only, leaving two scoring models in one bank and
     * silently changing what a percentage means depending on who wrote the question.
     *
     * The bounds are duplicated as literals because annotation values must be compile-time
     * constants; AssessmentConstants.MAX_OPTION_WEIGHT is the source of truth and the service
     * validates against it.
     */
    @NotNull(message = "Weight is required")
    @Min(value = 0, message = "Weight must be between 0 and 3")
    @Max(value = 3, message = "Weight must be between 0 and 3")
    private Integer weight;
}
