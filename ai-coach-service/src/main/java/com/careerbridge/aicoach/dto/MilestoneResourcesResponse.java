package com.careerbridge.aicoach.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The contract that distinguishes "no roadmap yet" from "roadmap exists, not enriched yet" at
 * zero extra cost: GET /resources returns [] for the former, and a list with every milestone
 * title present but resources: [] for the latter. See AiCoachResourceController's @Operation doc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneResourcesResponse {

    private String milestoneTitle;
    private List<ResourceItemResponse> resources;
}
