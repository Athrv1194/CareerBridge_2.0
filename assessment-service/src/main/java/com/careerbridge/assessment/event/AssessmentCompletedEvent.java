package com.careerbridge.assessment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Payload published to RabbitMQ when an attempt is submitted
 * (exchange careerbridge.exchange, routing key assessment.completed).
 *
 * Planned consumers are recommendation-service and notification-service; neither is built yet, so
 * this is currently published into an exchange with no bindings -- messages are discarded until a
 * consumer declares its queue. That is expected, not a fault.
 *
 * This class is the contract. Consumers should declare their own copy with matching field names and
 * take the concrete type in @RabbitListener, so Spring AMQP's default TypePrecedence.INFERRED
 * resolves the payload from the method signature rather than the sender's __TypeId__ header (which
 * names this package and would not resolve on their classpath).
 *
 * All fields are flat types -- no enums -- so adding a value on this side can never hard-fail a
 * consumer's Jackson binding.
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
