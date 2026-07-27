package com.careerbridge.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The full recommendation as the frontend consumes it.
 *
 * topRecommendations and otherCareers partition the same ranked list on
 * rank <= TOP_CAREERS_COUNT -- 3 and 4 careers today. Both stay in rank order, and neither is
 * gated on a minimum score, so topRecommendations is never empty.
 *
 * Invariant: overallMatchPercentage equals topRecommendations.get(0).matchPercentage, and
 * topCareerName equals its careerName. Both are read off this service's own rank 1 rather than
 * copied from the assessment event, so the response cannot contradict itself.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {

    private Long recommendationId;
    private Long userId;
    private String categoryName;
    private Double overallMatchPercentage;
    private String topCareerName;

    /** Ranks 1..TOP_CAREERS_COUNT. */
    private List<CareerRankingDto> topRecommendations;

    /** Ranks TOP_CAREERS_COUNT+1..N -- worth showing, not worth leading with. */
    private List<CareerRankingDto> otherCareers;

    private RecommendationReasonDto insight;

    private LocalDateTime createdAt;

    /** False once a newer assessment has superseded this one; history keeps the old rows. */
    private Boolean isActive;
}
