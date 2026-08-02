package com.careerbridge.aicoach.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Trimmed local copy of prs-service's PrsResponse -- all four scores plus the letter grade,
 * everything the coaching prompt needs from PRS in one call. All scores are Double on the real
 * response, verified against prs-service/.../dto/PrsResponse.java.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrsResponseDto {

    private Long studentId;
    private Double assessmentScore;
    private Double roadmapScore;
    private Double profileScore;
    private Double resumeScore;
    private Double totalScore;
    private String grade;
}
