package com.careerbridge.aicoach.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Trimmed local copy of roadmap-service's RoadmapResponse (GET /api/roadmap/my). Field is
 * milestone.title, NOT .name -- verified against roadmap-service's actual MilestoneResponse; the
 * supplied build prompt got this field name wrong.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapResponseDto {

    private Long studentId;
    private String careerName;
    private List<MilestoneDto> milestones;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MilestoneDto {
        private String title;
        private Integer orderIndex;
    }
}
