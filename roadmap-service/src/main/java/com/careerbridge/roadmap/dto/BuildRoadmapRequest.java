package com.careerbridge.roadmap.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Body of POST /api/roadmap. Matched against RoadmapTemplate.careerName, case-insensitively. */
@Data
public class BuildRoadmapRequest {

    @NotBlank(message = "careerName is required")
    private String careerName;
}
