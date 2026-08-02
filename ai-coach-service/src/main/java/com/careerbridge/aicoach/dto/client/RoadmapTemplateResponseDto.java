package com.careerbridge.aicoach.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Trimmed local copy of roadmap-service's RoadmapTemplateResponse (GET /api/roadmap/templates).
 * Used only by CatalogRefresher to fetch all 47 milestone titles across all 7 careers in one call.
 * Note the nested field is milestoneTemplates, NOT milestones -- roadmap-service's own DTO makes
 * this same distinction between a template's milestones and a student roadmap's milestones.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapTemplateResponseDto {

    private String careerName;
    private List<MilestoneTemplateDto> milestoneTemplates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MilestoneTemplateDto {
        private String title;
    }
}
