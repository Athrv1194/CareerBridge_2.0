package com.careerbridge.student.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Serves as both request body (POST/PUT .../experience) and response element. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceDto {

    private Long id;

    @NotBlank(message = "Title is required")
    private String title;

    private String company;

    private LocalDate startDate;

    private LocalDate endDate;

    @Builder.Default
    private Boolean isCurrent = false;

    private String description;
}
