package com.careerbridge.recruiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mirrors prs-service's PrsLeaderboardEntry field-for-field. See PublicStudentProfileDto's note. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrsLeaderboardEntryDto {

    private Integer rank;
    private Long studentId;
    private Double totalScore;
    private String grade;
}
