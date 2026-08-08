package com.careerbridge.prs.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * This service's own copy of what roadmap-service publishes with routing key "roadmap.updated".
 * Fields verified byte-for-byte against roadmap-service's own class.
 *
 * completionPercentage is the 30% input to the Placement Readiness Score. roadmap-service rounds it
 * to 2 decimals before publishing, so no further rounding is needed on the raw value here.
 *
 * prs-service is the FIRST consumer of this event. roadmap-service declared no queue for it and its
 * class comment says the intended consumer is "a future placement-service" -- that is this service,
 * under a shorter name. The queue and binding are declared here, per the ownership rule: consumers
 * own their queues, publishers declare only the exchange.
 *
 * The id field is studentId, not userId -- roadmap-service names it that way. Same person as
 * RecommendationGeneratedEvent.userId; both are the auth user id.
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
