package com.careerbridge.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentResultDto {

    private Long attemptId;

    private Long userId;

    private String categoryName;

    private Integer rawScore;

    private Integer maxPossibleScore;

    private Double categoryScorePercentage;

    private String topCareerPath;

    private Double careerMatchPercentage;

    /** The top-N career matches, parsed from AssessmentResult.allCareerScoresJson. */
    private Map<String, Double> allCareerScores;

    private LocalDateTime calculatedAt;
}
