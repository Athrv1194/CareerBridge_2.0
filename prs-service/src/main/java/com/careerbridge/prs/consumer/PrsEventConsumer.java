package com.careerbridge.prs.consumer;

import com.careerbridge.prs.config.RabbitMQConfig;
import com.careerbridge.prs.event.RecommendationGeneratedEvent;
import com.careerbridge.prs.event.RoadmapUpdatedEvent;
import com.careerbridge.prs.event.StudentRegisteredEvent;
import com.careerbridge.prs.service.PrsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * The three inbound legs of the Placement Readiness Score, one listener per queue.
 *
 * Three methods in one class is fine; three methods on one QUEUE would not be. Spring AMQP creates
 * a container per @RabbitListener, and RabbitMQ round-robins deliveries between containers sharing
 * a queue -- so roughly a third of each event type would be handed to the wrong method. Jackson 3
 * has FAIL_ON_UNKNOWN_PROPERTIES off, so that does not throw; it produces an all-null object the
 * null guards discard with a WARN, and most score updates vanish with no error anywhere. Each
 * method below names a distinct queue, which is what makes this safe.
 *
 * Every parameter is the concrete event type on purpose. Spring AMQP's default
 * TypePrecedence.INFERRED resolves the payload from the method signature, so the sender's __TypeId__
 * header -- which names a package absent from this classpath -- is never read. Widening any of these
 * to Object or Message falls back to that header and fails with ClassNotFoundException on every
 * event.
 *
 * Not @Transactional. Each PrsService method carries its own boundary, which opens and closes inside
 * the proxied call, so by the time an exception reaches a catch below it has already rolled back.
 */
@Component
public class PrsEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PrsEventConsumer.class);

    private final PrsService prsService;

    public PrsEventConsumer(PrsService prsService) {
        this.prsService = prsService;
    }

    /**
     * Bound to student.registered, NOT user.registered -- see RabbitMQConfig for why that
     * distinction is load-bearing rather than cosmetic.
     *
     * organizationId is harvested here and nowhere else: this is the only inbound event that
     * carries it, and it is what scopes the ORG_ADMIN leaderboard.
     */
    @RabbitListener(queues = RabbitMQConfig.PRS_USER_QUEUE)
    public void onStudentRegistered(StudentRegisteredEvent event) {
        try {
            if (event == null) {
                log.warn("Ignoring null {} payload", RabbitMQConfig.STUDENT_REGISTERED_ROUTING_KEY);
                return;
            }
            prsService.initialisePrs(event.getUserId(), event.getOrganizationId());
        } catch (Exception ex) {
            // Fail-soft: rethrowing requeues the message and spins the listener forever on a
            // payload that will never succeed. The expected exception here is
            // DataIntegrityViolationException from uk_prs_student_id on a duplicate delivery --
            // the constraint doing its job, not a fault.
            log.error("Failed to handle {} for userId={}: {}",
                    RabbitMQConfig.STUDENT_REGISTERED_ROUTING_KEY,
                    event == null ? null : event.getUserId(),
                    ex.getMessage());
        }
    }

    /** 40% input. Note the id field here is userId; roadmap-service names the same person studentId. */
    @RabbitListener(queues = RabbitMQConfig.PRS_RECOMMENDATION_QUEUE)
    public void onRecommendationGenerated(RecommendationGeneratedEvent event) {
        try {
            if (event == null) {
                log.warn("Ignoring null {} payload", RabbitMQConfig.RECOMMENDATION_GENERATED_ROUTING_KEY);
                return;
            }
            prsService.updateAssessmentScore(event.getUserId(), event.getMatchPercentage());
        } catch (Exception ex) {
            log.error("Failed to handle {} for userId={}: {}",
                    RabbitMQConfig.RECOMMENDATION_GENERATED_ROUTING_KEY,
                    event == null ? null : event.getUserId(),
                    ex.getMessage());
        }
    }

    /**
     * 30% input. Fires on every milestone completion, so a student walking a 7-milestone roadmap
     * produces seven of these and seven prs.updated events -- intended, since the score genuinely
     * changes each time.
     */
    @RabbitListener(queues = RabbitMQConfig.PRS_ROADMAP_QUEUE)
    public void onRoadmapUpdated(RoadmapUpdatedEvent event) {
        try {
            if (event == null) {
                log.warn("Ignoring null {} payload", RabbitMQConfig.ROADMAP_UPDATED_ROUTING_KEY);
                return;
            }
            prsService.updateRoadmapScore(event.getStudentId(), event.getCompletionPercentage());
        } catch (Exception ex) {
            log.error("Failed to handle {} for studentId={}: {}",
                    RabbitMQConfig.ROADMAP_UPDATED_ROUTING_KEY,
                    event == null ? null : event.getStudentId(),
                    ex.getMessage());
        }
    }
}
