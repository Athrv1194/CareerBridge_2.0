package com.careerbridge.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The narrative half of a recommendation: strength, gap, next step. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationReasonDto {

    private String strengthArea;
    private String improvementArea;
    private String actionableAdvice;
}
