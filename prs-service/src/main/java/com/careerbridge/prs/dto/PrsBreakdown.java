package com.careerbridge.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The arithmetic behind totalScore, made visible.
 *
 * This exists so the score is auditable rather than a bare number a student has to trust: each
 * input appears with its weight and its weighted contribution, and the four contributions sum to
 * totalScore exactly. A student who is stuck at 62 can read straight off this which lever to pull.
 *
 * reservedWeight/reservedContribution are the reason the composite tops out at 90 rather than 100:
 * 10% is held back for the future resume builder and contributes 0.0 today. Reporting it as a
 * visible zero is the whole point -- omitting it would leave ten points unaccounted for and make
 * the total look like a rounding bug.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrsBreakdown {

    /** Always 40. A field rather than a constant on the client so the UI never hardcodes weights. */
    private Integer assessmentWeight;

    /** Raw 0-100 input: matchPercentage from the student's latest recommendation. */
    private Double assessmentScore;

    /** assessmentScore x 0.40. */
    private Double assessmentContribution;

    /** Always 30. */
    private Integer roadmapWeight;

    /** Raw 0-100 input: completionPercentage of the student's roadmap. */
    private Double roadmapScore;

    /** roadmapScore x 0.30. */
    private Double roadmapContribution;

    /** Always 20. */
    private Integer profileWeight;

    /** Raw 0-100 input: profileCompletionPercentage from student-service. */
    private Double profileScore;

    /** profileScore x 0.20. */
    private Double profileContribution;

    /** Always 10 -- reserved for the future resume builder. */
    private Integer reservedWeight;

    /** Always 0.0 until the resume builder ships. */
    private Double reservedContribution;

    /** Equals the sum of the four contributions above. */
    private Double totalScore;
}
