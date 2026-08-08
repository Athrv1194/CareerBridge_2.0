package com.careerbridge.recommendation.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Consumer-side copy of the payload assessment-service publishes when an attempt is submitted
 * (exchange careerbridge.exchange, routing key assessment.completed).
 *
 * Field names and JSON shape must stay in lockstep with
 * com.careerbridge.assessment.event.AssessmentCompletedEvent. The differing package is fine: the
 * listener method takes this concrete type, and Spring AMQP's default TypePrecedence.INFERRED
 * resolves the payload from the method signature without ever reading the sender's __TypeId__
 * header (which names assessment-service's package and would not resolve here).
 *
 * allCareerScores now carries assessment-service's own already-computed, already-graduated
 * per-career map -- used as the primary ranking source in rankCareers, not recomputed from
 * categoryName. That used to be the whole point of this event lacking the map (relevance depended
 * only on the category name), but assessment-service now always sends categoryName="Overall" for
 * the one aggregated event a 3-section run produces, and no career's requiredSkills ever mentions
 * "Overall" -- so re-deriving locally from just the name flattened every career to the same score,
 * no matter how the local heuristic was tuned. RecommendationEngine.calculateMatchScore is kept
 * only as a defensive fallback for a career missing from the map.
 *
 * topCareerPath and careerMatchPercentage are nullable -- assessment-service sends null for both
 * when its career_paths table is empty. They are used only as a tie-break hint, never as the
 * source of this service's own rank 1.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentCompletedEvent {

    private Long userId;
    private Long attemptId;
    private Long categoryId;
    private String categoryName;
    private Double categoryScorePercentage;
    private String topCareerPath;
    private Double careerMatchPercentage;
    private LocalDateTime completedAt;
    private Map<String, Double> allCareerScores;
}
