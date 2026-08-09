package com.careerbridge.student.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Serves as both request body (POST .../projects) and response element. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDto {

    private Long id;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    /** Comma-separated technologies, stored as a single string. */
    private String techStack;

    private String projectUrl;

    private String githubUrl;

    private LocalDate startDate;

    private LocalDate endDate;

    @Builder.Default
    private Boolean isOngoing = false;

    /** Whether a cover image is stored -- never the bytes themselves; fetched separately. */
    private Boolean hasCoverImage;
}
