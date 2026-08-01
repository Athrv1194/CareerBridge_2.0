package com.careerbridge.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminQuestionRequest {

    /** Maps to Question.questionText; the entity's column is TEXT, so there is no length cap here. */
    @NotBlank(message = "Question text is required")
    private String text;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    /**
     * 1-based. The entity carries a unique constraint on (category_id, order_index), so a clash is a
     * DataIntegrityViolationException the service has to translate rather than a validation error.
     */
    @NotNull(message = "Order index is required")
    @Min(value = 1, message = "Order index must be at least 1")
    private Integer orderIndex;

    /** @Builder.Default, or Lombok's builder writes null straight past the initialiser. */
    @Builder.Default
    private Boolean isActive = true;

    /**
     * @Valid is what makes the constraints on AdminOptionRequest actually run -- without it the list
     * is checked for size only and each element's @NotBlank/@NotNull is silently skipped.
     *
     * 2..4 because a single-choice question is not a choice and the seeded bank never exceeds four.
     */
    @NotEmpty(message = "Options are required")
    @Size(min = 2, max = 4, message = "Question must have between 2 and 4 options")
    @Valid
    private List<AdminOptionRequest> options;
}
