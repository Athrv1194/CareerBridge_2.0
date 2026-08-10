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

    private Double resumeScore;

    /**
     * 5.0 per completed mentorship session, capped at 100. Reported here but NOT included in
     * totalScore or in breakdown -- the four weights already sum to 1.00. See
     * PlacementReadinessScore.mentoringScore.
     */
    private Double mentoringScore;

    /** Weighted composite of the four weighted inputs, 0-100. Excludes mentoringScore. */
    private Double totalScore;

    /** A / B / C / D / F. */
    private String grade;

    private PrsBreakdown breakdown;

    private LocalDateTime lastUpdatedAt;
}
