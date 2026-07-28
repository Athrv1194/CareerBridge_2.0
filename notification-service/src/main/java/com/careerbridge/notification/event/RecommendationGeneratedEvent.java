package com.careerbridge.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Consumer-side copy of the payload recommendation-service publishes when a recommendation is
 * generated (exchange careerbridge.exchange, routing key recommendation.generated).
 *
 * Field names and JSON shape must stay in lockstep with
 * com.careerbridge.recommendation.event.RecommendationGeneratedEvent. The differing package is
 * fine: the listener method takes this concrete type, and Spring AMQP's default
 * TypePrecedence.INFERRED resolves the payload from the method signature without ever reading the
 * sender's __TypeId__ header (which names recommendation-service's package and would not resolve
 * here).
 *
 * Note what this does NOT carry: any recipient email or student name. That is why this service
 * also consumes student.registered and keeps a UserContact row -- see NotificationServiceImpl.
 *
 * matchPercentage is a nullable Double. Anything formatting it must null-coalesce first:
 * String.format("%.1f%%", (Object) null) throws NullPointerException.
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
