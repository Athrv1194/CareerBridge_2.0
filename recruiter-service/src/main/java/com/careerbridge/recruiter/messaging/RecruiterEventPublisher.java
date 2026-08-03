package com.careerbridge.recruiter.messaging;

import com.careerbridge.recruiter.config.RabbitMQConfig;
import com.careerbridge.recruiter.event.ApplicationStatusUpdatedEvent;
import com.careerbridge.recruiter.event.ApplicationSubmittedEvent;
import com.careerbridge.recruiter.event.PlacementCompletedEvent;
import com.careerbridge.recruiter.model.enums.ApplicationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Fail-soft on all three publishes: the JobApplication row is already committed by the time any of
 * these methods run, so rethrowing on a broker outage would report failure for work that actually
 * happened. Same pattern as organization-service's organization.created and roadmap-service's
 * roadmap.updated.
 */
@Component
public class RecruiterEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RecruiterEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RecruiterEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishApplicationSubmitted(Long jobId, Long studentId, Long recruiterId) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.APPLICATION_SUBMITTED_ROUTING_KEY,
                    ApplicationSubmittedEvent.builder()
                            .jobId(jobId)
                            .studentId(studentId)
                            .recruiterId(recruiterId)
                            .submittedAt(LocalDateTime.now())
                            .build());
        } catch (Exception ex) {
            log.warn("Failed to publish {} for jobId={} studentId={}: {}",
                    RabbitMQConfig.APPLICATION_SUBMITTED_ROUTING_KEY, jobId, studentId, ex.getMessage());
        }
    }

    public void publishApplicationStatusUpdated(Long applicationId, Long studentId, Long jobId,
                                                 ApplicationStatus newStatus) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.APPLICATION_STATUS_UPDATED_ROUTING_KEY,
                    ApplicationStatusUpdatedEvent.builder()
                            .applicationId(applicationId)
                            .studentId(studentId)
                            .jobId(jobId)
                            .newStatus(newStatus)
                            .updatedAt(LocalDateTime.now())
                            .build());
        } catch (Exception ex) {
            log.warn("Failed to publish {} for applicationId={}: {}",
                    RabbitMQConfig.APPLICATION_STATUS_UPDATED_ROUTING_KEY, applicationId, ex.getMessage());
        }
    }

    /**
     * Fired when a student ACCEPTS an offer -- the moment a placement actually happens. An extended
     * offer is not a placement, since the student may still decline it.
     *
     * The event is built by the caller rather than assembled from loose arguments here: it needs
     * the job title and company name, which the service layer has already loaded for its own
     * response and would otherwise have to be re-fetched.
     */
    public void publishPlacementCompleted(PlacementCompletedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.PLACEMENT_COMPLETED_ROUTING_KEY,
                    event);
            log.info("Published {} for studentId={} company={} ctc={} LPA",
                    RabbitMQConfig.PLACEMENT_COMPLETED_ROUTING_KEY,
                    event.getStudentId(), event.getCompanyName(), event.getOfferedCtc());
        } catch (Exception ex) {
            log.warn("Failed to publish {} for applicationId={}: {}",
                    RabbitMQConfig.PLACEMENT_COMPLETED_ROUTING_KEY,
                    event.getApplicationId(), ex.getMessage());
        }
    }
}
