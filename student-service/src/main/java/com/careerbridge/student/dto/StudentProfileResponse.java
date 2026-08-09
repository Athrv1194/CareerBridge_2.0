package com.careerbridge.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Body of GET/PUT /api/student/profile: the flat profile plus its four child collections. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileResponse {

    private Long id;

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

    private String resumeUrl;

    /** Whether an avatar photo is stored -- never the bytes themselves; fetched separately. */
    private Boolean hasAvatar;

    private Integer profileCompletionPercentage;

    private Boolean isPublic;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<EducationDto> educations;

    private List<SkillDto> skills;

    private List<ProjectDto> projects;

    private List<CertificateDto> certificates;

    private List<ExperienceDto> experiences;
}
