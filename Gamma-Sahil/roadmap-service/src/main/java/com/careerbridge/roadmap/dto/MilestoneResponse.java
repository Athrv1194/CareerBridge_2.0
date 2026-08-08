package com.careerbridge.roadmap.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneResponse {

    private Long id;

    private String title;

    private String description;

    private Integer orderIndex;

    private Integer estimatedDays;

    private String resourceUrl;

    /**
     * Boxed Boolean, never a primitive. A primitive makes Lombok emit isCompleted() rather than
     * getIsCompleted(), which serialises the field as "completed" and silently breaks the frontend
     * contract. Same trap as notification-service's isRead.
     */
    private Boolean isCompleted;

    private LocalDateTime completedAt;
}
