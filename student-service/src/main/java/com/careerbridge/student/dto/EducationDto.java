package com.careerbridge.student.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Serves as both request body (POST .../education) and response element inside StudentProfileResponse. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationDto {

    /** Server-assigned; ignored on create, populated on read. */
    private Long id;

    @NotBlank(message = "Institution is required")
    private String institution;

    private String degree;

    private String fieldOfStudy;

    private Integer startYear;

    private Integer endYear;

    private String grade;

    private String description;
}
