package com.careerbridge.roadmap.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneTemplateResponse {

    private Long id;

    private String title;

    private String description;

    private Integer orderIndex;

    private Integer estimatedDays;

    private String resourceUrl;
}
