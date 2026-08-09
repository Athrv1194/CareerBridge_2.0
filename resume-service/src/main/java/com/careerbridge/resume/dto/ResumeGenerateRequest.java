package com.careerbridge.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional body for POST /api/resume/generate. Every field is nullable and defaults to true (for
 * the include* toggles) so an old caller sending no body at all -- or a partial body -- still gets
 * the same "everything on" behaviour this endpoint always had.
 *
 * jobDescription, when non-blank, switches scoring to "tailor mode": the ATS score is computed
 * against keywords extracted from this text instead of the best-matching one of the seven fixed
 * careers. See AtsScoreCalculator.calculateTailored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeGenerateRequest {

    private String summary;

    private Boolean includePhone;
    private Boolean includeEmail;
    private Boolean includeLinks;
    private Boolean includeLocation;

    private Boolean includeExperience;
    private Boolean includeSkills;
    private Boolean includeProjects;
    private Boolean includeEducation;
    private Boolean includeCertificates;

    private String jobDescription;

    /** Null-safe default: missing means "on", matching this endpoint's pre-toggle behaviour. */
    public boolean orDefaultTrue(Boolean value) {
        return value == null || value;
    }
}
