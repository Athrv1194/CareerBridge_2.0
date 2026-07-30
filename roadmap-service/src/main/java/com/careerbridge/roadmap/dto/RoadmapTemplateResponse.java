package com.careerbridge.roadmap.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The admin view of a seeded blueprint. Deliberately carries no isActive: getAllTemplates only ever
 * returns active ones, so the field would be true on every row and read as if it could be false.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapTemplateResponse {

    private Long id;

    private String careerName;

    private String templateTitle;

    private String description;

    private Integer totalMilestones;

    private Integer estimatedWeeks;

    private List<MilestoneTemplateResponse> milestoneTemplates;
}
