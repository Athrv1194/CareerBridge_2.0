package com.careerbridge.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Resume metadata. Deliberately carries no PDF bytes -- those are only ever served by
 * GET /api/resume/download/{id}, which streams them as application/pdf.
 *
 * fileUrl is derived from the id at mapping time, not stored: see StudentResume's class comment.
 *
 * The ATS breakdown fields (closestCareerName, matchedKeywords, ...) and the builder options
 * (summary, include*, jobDescription) used to be computed and then discarded, or never accepted at
 * all -- ResumeResponse only ever returned the bare atsScore. Every field below now round-trips
 * what AtsScoreCalculator actually computes and what ResumeGenerateRequest actually accepted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeResponse {

    private Long id;
    private Long studentId;
    private String fileName;
    private String fileUrl;
    private Integer version;
    private Double atsScore;
    private Boolean isDefault;
    private LocalDateTime generatedAt;

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

    private Boolean isTailored;
    private String jobDescription;
    private String closestCareerName;
    private List<String> matchedKeywords;
    private List<String> missingKeywords;
    private Integer totalKeywords;
    private Boolean hasEducation;
    private Boolean hasProjects;
}
