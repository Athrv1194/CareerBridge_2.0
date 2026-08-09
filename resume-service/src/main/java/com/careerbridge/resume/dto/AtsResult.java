package com.careerbridge.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The full ATS breakdown AtsScoreCalculator computes internally but, before this, only ever
 * discarded down to a bare score. Every field here is now returned to the caller via ResumeResponse.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtsResult {

    private Double score;

    /** Null in tailor mode -- there is no single "closest career" once matching an ad hoc job text. */
    private String closestCareerName;

    private List<String> matchedKeywords;
    private List<String> missingKeywords;
    private Integer totalKeywords;
}
