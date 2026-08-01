package com.careerbridge.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin view of an option. Unlike the student-facing OptionDto, which deliberately has no weight
 * field at all, this one exposes it -- an admin editing the question bank has to see and set the
 * scoring gradient.
 *
 * Consequence: this DTO must never be returned from a student-facing endpoint. A client that can
 * see weights knows the highest-scoring answer to every question.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminOptionResponse {

    private Long id;
    private String text;

    /** Graded 0..3; see AdminOptionRequest.weight for why this is not a boolean. */
    private Integer weight;
}
