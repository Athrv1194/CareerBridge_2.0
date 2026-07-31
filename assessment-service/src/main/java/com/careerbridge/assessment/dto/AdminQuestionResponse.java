package com.careerbridge.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin view of a question, including its options and which of them is correct.
 *
 * categoryName is denormalised from the Category table for display -- Question holds only
 * categoryId, so the service resolves the name rather than the client making a second call.
 *
 * updatedAt is null for every seeded question until an admin first edits it: @UpdateTimestamp only
 * fires on Hibernate's update path and data.sql bypasses it entirely.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminQuestionResponse {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private String text;
    private Integer orderIndex;
    private Boolean isActive;
    private List<AdminOptionResponse> options;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
