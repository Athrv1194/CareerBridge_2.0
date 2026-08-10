package com.careerbridge.mentor.messaging;

import com.careerbridge.mentor.config.MentorRabbitMQConfig;
import com.careerbridge.mentor.event.SessionAcceptedEvent;
import com.careerbridge.mentor.event.SessionBookedEvent;
import com.careerbridge.mentor.event.SessionCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;

/**
 * The three outbound events, all fail-soft.
 *
 * Every publish here happens inside the caller's @Transactional method, after the row it describes
 * has been written to that transaction. That is the same shape prs-service, recruiter-service and
 * resume-service use, and it carries the same acknowledged window: a consumer could in principle
 * read before the outer commit flushes. It is the right trade here for the reason it was wrong in
 * payment-service -- a phantom session.completed costs a lagging mentoring score and one premature
 * "leave a review" email, both self-correcting, whereas a phantom subscription.activated would have
 * granted a free premium plan off a rolled-back payment.
 *
 * Fail-soft is load-bearing rather than defensive: by the time these run the session row is already
 * committed to the transaction, so rethrowing would report failure for work that actually happened
 * and the caller's retry would then trip a status guard ("Session is not in REQUESTED status").
 */
@Component
public class MentorEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MentorEventPublisher.class);

    private final AmqpTemplate amqpTemplate;

    public MentorEventPublisher(AmqpTemplate amqpTemplate) {
        this.amqpTemplate = amqpTemplate;
    }

    public void publishSessionBooked(SessionBookedEvent event) {
        publish(MentorRabbitMQConfig.SESSION_BOOKED_ROUTING_KEY, event, event.getSessionId());
    }

    public void publishSessionAccepted(SessionAcceptedEvent event) {
        publish(MentorRabbitMQConfig.SESSION_ACCEPTED_ROUTING_KEY, event, event.getSessionId());
    }

    public void publishSessionCompleted(SessionCompletedEvent event) {
        publish(MentorRabbitMQConfig.SESSION_COMPLETED_ROUTING_KEY, event, event.getSessionId());
    }

    private void publish(String routingKey, Object payload, Long sessionId) {
        try {
            amqpTemplate.convertAndSend(MentorRabbitMQConfig.EXCHANGE, routingKey, payload);
            log.info("Published {} for sessionId={}", routingKey, sessionId);
        } catch (Exception ex) {
            log.warn("Failed to publish {} for sessionId={}: {}", routingKey, sessionId, ex.getMessage());
        }
    }
}
