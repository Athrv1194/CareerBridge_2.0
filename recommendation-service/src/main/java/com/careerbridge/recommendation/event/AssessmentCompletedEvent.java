package com.careerbridge.recommendation.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
 * Note what this does NOT carry: any collection of per-career scores. Eight scalars, no map. The
 * full ranking is recomputed locally from categoryName + categoryScorePercentage against
 * CareerCatalog, because relevance depends only on the category name.
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
}
