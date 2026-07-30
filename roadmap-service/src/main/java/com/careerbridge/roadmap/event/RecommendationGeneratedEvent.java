package com.careerbridge.roadmap.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * This service's own copy of the payload recommendation-service publishes on
 * careerbridge.exchange with routing key recommendation.generated. Field names and types are
 * byte-identical to recommendation-service's event/RecommendationGeneratedEvent.java and to what
 * RecommendationServiceImpl.publishGenerated actually builds -- a rename on either side breaks
 * binding silently, since Jackson 3 has FAIL_ON_UNKNOWN_PROPERTIES off and simply leaves the field
 * null. notification-service holds a third copy of the same contract.
 *
 * A separate copy rather than a shared module on purpose: there is no shared library in this
 * project, and duplicating the flat payload is what lets the two services deploy independently.
 *
 * All fields are flat types -- no enums -- so recommendation-service adding a value can never
 * hard-fail this consumer's binding.
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
