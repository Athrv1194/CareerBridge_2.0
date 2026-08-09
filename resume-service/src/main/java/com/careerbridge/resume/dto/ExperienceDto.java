package com.careerbridge.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Mirrors student-service's ExperienceDto field-for-field. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceDto {

    private String title;
    private String company;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isCurrent;
    private String description;
}
