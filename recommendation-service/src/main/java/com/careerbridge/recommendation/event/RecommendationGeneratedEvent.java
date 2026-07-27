package com.careerbridge.recommendation.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Payload published when a recommendation is generated
 * (exchange careerbridge.exchange, routing key recommendation.generated).
 *
 * Intended consumer is notification-service, which is not built yet -- so this currently goes into
 * an exchange with no binding for this key and is discarded. Expected, not a fault; verify
 * publishing through the RabbitMQ console rather than a downstream service.
 *
 * This class is the contract. Consumers should declare their own copy with matching field names and
 * take the concrete type in @RabbitListener so TypePrecedence.INFERRED resolves from the method
 * signature instead of the __TypeId__ header naming this package.
 *
 * All fields are flat types -- no enums -- so adding a value here can never hard-fail a consumer's
 * Jackson binding.
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
