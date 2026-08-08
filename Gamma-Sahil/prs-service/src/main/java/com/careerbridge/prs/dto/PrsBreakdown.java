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
 * resumeWeight/resumeContribution were named reservedWeight/reservedContribution before
 * resume-service shipped, when this 10% genuinely was unallocated. Renamed rather than left stale
 * because "reserved" would now be actively misleading: the slot is live, not held back. A student
 * who has never generated a resume simply sees resumeContribution at 0.0, which reads the same as
 * "reserved" did but is now an honest true zero rather than a permanently disabled field.
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

    /** Always 10. */
    private Integer resumeWeight;

    /** Raw 0-100 input: the ATS score of the student's latest generated resume. 0.0 if none exists. */
    private Double resumeScore;

    /** resumeScore x 0.10. */
    private Double resumeContribution;

    /** Equals the sum of the four contributions above. */
    private Double totalScore;
}
