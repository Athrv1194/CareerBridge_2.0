package com.careerbridge.prs.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * This service's own copy of what recommendation-service publishes with routing key
 * "recommendation.generated". Fields verified byte-for-byte against that service's own class and
 * against what RecommendationServiceImpl.publishGenerated actually builds.
 *
 * matchPercentage is the 40% input to the Placement Readiness Score. Note it is the student's
 * career-match figure, not their raw assessment percentage: recommendation-service damps the
 * category score by a relevance heuristic, so today it ceilings around 30.0 (CLAUDE.md documents
 * the 7-way tie). That is upstream behaviour, not a defect here -- this service stores whatever
 * arrives.
 *
 * The id field is userId, not studentId. roadmap-service's event names the same person studentId;
 * both are the auth user id. Do not "harmonise" one to match the other -- each mirrors its
 * publisher exactly, and renaming a field silently breaks Jackson binding.
 *
 * Third consumer of this event, after notification-service and roadmap-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationGeneratedEvent {

    private Long userId;
    private Long recommendationId;
    private String topCareerName;
    private Double matchPercentage;
    private String categoryName;
    private LocalDateTime generatedAt;
}
