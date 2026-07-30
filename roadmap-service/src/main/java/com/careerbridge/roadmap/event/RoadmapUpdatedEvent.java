package com.careerbridge.roadmap.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Published on careerbridge.exchange with routing key roadmap.updated every time a student
 * completes a milestone.
 *
 * Nothing consumes it yet -- the intended consumer is the future placement-service, where
 * completionPercentage carries 30% of the Placement Readiness Score. No queue is bound to this key,
 * so these messages are currently discarded by the exchange. That is expected, not a fault: verify
 * publication through the RabbitMQ console or a temporary probe queue, not a downstream service. A
 * future consumer declares its own queue and binding, per this project's queue-ownership rule.
 *
 * Flat types only, for the same forward-compatibility reason as RecommendationGeneratedEvent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapUpdatedEvent {

    private Long studentId;
    private Long roadmapId;
    private Double completionPercentage;
    private String status;
    private LocalDateTime updatedAt;
}
