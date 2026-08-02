package com.careerbridge.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mirrors student-service's EducationDto field-for-field. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationDto {

    private String institution;
    private String degree;
    private String fieldOfStudy;
    private Integer startYear;
    private Integer endYear;
    private String grade;
    private String description;
}
