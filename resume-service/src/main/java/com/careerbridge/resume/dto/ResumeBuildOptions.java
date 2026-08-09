package com.careerbridge.resume.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Resolved builder options passed to ResumePdfBuilder -- every include* flag here is already
 * defaulted (see ResumeGenerateRequest.orDefaultTrue), so the PDF layer never has to handle null.
 * Kept separate from ResumeGenerateRequest so the PDF package never depends on the request/model
 * layers, only on this and StudentProfileDto.
 */
@Data
@Builder
public class ResumeBuildOptions {

    private String summary;

    private boolean includePhone;
    private boolean includeEmail;
    private boolean includeLinks;
    private boolean includeLocation;

    private boolean includeExperience;
    private boolean includeSkills;
    private boolean includeProjects;
    private boolean includeEducation;
    private boolean includeCertificates;
}
