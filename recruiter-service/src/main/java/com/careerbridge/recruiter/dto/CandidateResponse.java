package com.careerbridge.recruiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One candidate-search hit: student-service supplies the identity and skills, prs-service the
 * score.
 *
 * There is deliberately no careerPath field. The student's top recommended career lives in
 * recommendation-service behind GET /api/recommendation/my, which is keyed on X-User-Id -- reading
 * it would mean a third REST client and one more synchronous call per candidate, on top of the two
 * batch calls this search already makes. Add it only when there is a batch source for it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateResponse {

    private Long studentId;
    private String firstName;
    private String lastName;
    private String email;
    private List<String> skills;

    /**
     * Placement Readiness Score, 0-90 in practice. SCORE_UNAVAILABLE (-1.0) means prs-service held
     * no row for this student or was unreachable -- deliberately outside the legal range so it
     * cannot be mistaken for a real score. Same sentinel convention as prs-service's own
     * StudentServiceClient.PROFILE_FETCH_FAILED.
     */
    private Double prsScore;

    private Integer profileCompletionPercentage;

    /** Convenience link for the recruiter UI; this service does not serve it. */
    private String profileUrl;
}
