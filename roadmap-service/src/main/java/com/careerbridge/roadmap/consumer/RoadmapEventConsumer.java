package com.careerbridge.roadmap.consumer;

import com.careerbridge.roadmap.config.RabbitMQConfig;
import com.careerbridge.roadmap.event.RecommendationGeneratedEvent;
import com.careerbridge.roadmap.service.RoadmapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class RoadmapEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RoadmapEventConsumer.class);

    private final RoadmapService roadmapService;

    public RoadmapEventConsumer(RoadmapService roadmapService) {
        this.roadmapService = roadmapService;
    }

    /**
     * Turns a fresh recommendation into the student's milestone checklist, so they never have to
     * ask for a roadmap as a separate step.
     *
     * The parameter is the concrete event type on purpose: Spring AMQP's default
     * TypePrecedence.INFERRED resolves the payload from this signature, so the sender's __TypeId__
     * header -- which names recommendation-service's package, absent from this classpath -- is
     * never read. Widening this to Object or Message falls back to that header and fails with
     * ClassNotFoundException on every event.
     *
     * One listener on one queue. A second @RabbitListener method on careerbridge.roadmap.queue
     * would create a second container consuming it, and RabbitMQ round-robins between them --
     * roughly half of each event type would bind into the wrong class, and since Jackson 3 has
     * FAIL_ON_UNKNOWN_PROPERTIES off that yields a silently all-null object rather than an error.
     * A new event type gets its own queue.
     *
     * Not @Transactional here. RoadmapServiceImpl.generateRoadmap carries its own boundary, which
     * opens and closes inside that call, so by the time an exception reaches the catch below it has
     * already rolled back.
     */
    @RabbitListener(queues = RabbitMQConfig.ROADMAP_QUEUE)
    public void onRecommendationGenerated(RecommendationGeneratedEvent event) {
        try {
            roadmapService.generateRoadmap(event);
        } catch (Exception ex) {
            // Fail-soft: rethrowing would requeue the message and spin the listener forever on a
            // payload that will never succeed. A missing roadmap is recoverable by retaking an
            // assessment; an infinite redelivery loop takes the consumer down for every student.
            // The expected exception here is DataIntegrityViolationException from the
            // (student_id, recommendation_id) unique constraint when a delivery is duplicated --
            // which is the constraint doing its job, not a fault.
            log.error("Failed to handle {} for userId={}: {}",
                    RabbitMQConfig.RECOMMENDATION_GENERATED_ROUTING_KEY,
                    event == null ? null : event.getUserId(),
                    ex.getMessage());
        }
    }
}
