package com.careerbridge.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A career path from the local catalogue. Not an entity: nothing in this service's schema
 * references a career by id -- CareerRanking.careerName and Recommendation.topCareerName are both
 * plain Strings -- so there is no FK, no join and no write path to justify a table.
 *
 * requiredSkills is the comma-separated string the relevance heuristic substring-matches the
 * assessment category name against; see RecommendationEngine.calculateMatchScore.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerPathDto {

    private String name;
    private String description;
    private String requiredSkills;
}
