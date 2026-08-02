package com.careerbridge.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A deliberately partial local copy of student-service's StudentProfileResponse -- that class has
 * 22 fields; this one takes the ones a resume PDF actually renders. Safe because Jackson 3 disables
 * FAIL_ON_UNKNOWN_PROPERTIES by default (Boot 4 ships tools.jackson), so the fields not declared
 * here are simply ignored rather than throwing. Same convention as prs-service's own trimmed copy
 * of this response.
 *
 * No achievements, no socialLinks, no recommendedCareerPath: none of those exist on
 * StudentProfileResponse. Verified byte-for-byte against
 * student-service/src/main/java/com/careerbridge/student/dto/StudentProfileResponse.java before
 * this was written; re-verify after any change there.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileDto {

    private Long userId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String bio;
    private String city;
    private String state;
    private String country;
    private String linkedinUrl;
    private String githubUrl;
    private String portfolioUrl;

    private List<EducationDto> educations;
    private List<SkillDto> skills;
    private List<ProjectDto> projects;
    private List<CertificateDto> certificates;
}
