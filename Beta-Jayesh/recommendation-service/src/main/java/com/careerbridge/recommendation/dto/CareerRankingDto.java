package com.careerbridge.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One career's standing in a recommendation. Appears in both halves of RecommendationResponse. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerRankingDto {

    private String careerName;
    private Double matchPercentage;

    /** 1-based, ascending by rank = descending by matchPercentage. */
    private Integer rank;

    /** rank <= TOP_CAREERS_COUNT. Redundant with which list holds it, but explicit for clients. */
    private Boolean isTopRecommendation;

    private String reasonText;
}
