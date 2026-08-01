package com.careerbridge.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Body of GET /api/prs/my and GET /api/prs/{studentId}.
 *
 * Carries both the flat scores and the breakdown that explains them. The three raw scores are
 * repeated here rather than only inside breakdown so a client that just wants the headline numbers
 * never has to reach into a nested object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrsResponse {

    private Long studentId;

    /** Raw 0-100 inputs, unweighted. */
    private Double assessmentScore;

    private Double roadmapScore;

    private Double profileScore;

    /** Weighted composite, 0-90 in practice. See PrsBreakdown for why not 100. */
    private Double totalScore;

    /** A / B / C / D / F. */
    private String grade;

    private PrsBreakdown breakdown;

    private LocalDateTime lastUpdatedAt;
}
