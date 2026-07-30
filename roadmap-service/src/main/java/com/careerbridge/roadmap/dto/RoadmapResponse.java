package com.careerbridge.roadmap.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The student-facing roadmap. milestones always arrives ordered by orderIndex -- the dashboard
 * renders it as a checklist top to bottom and does not sort.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapResponse {

    private Long id;

    private Long studentId;

    private String careerName;

    /** IN_PROGRESS, COMPLETED or PAUSED. */
    private String status;

    private Integer totalMilestones;

    private Integer completedMilestones;

    private Double completionPercentage;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private List<MilestoneResponse> milestones;
}
