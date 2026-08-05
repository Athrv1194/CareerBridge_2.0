package com.careerbridge.notification.consumer;

import com.careerbridge.notification.constants.NotificationConstants;
import com.careerbridge.notification.event.RecommendationGeneratedEvent;
import com.careerbridge.notification.event.StudentRegisteredEvent;
import com.careerbridge.notification.event.SubscriptionActivatedEvent;
import com.careerbridge.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Two listeners on two separate queues -- see RabbitMQConfig for why one shared queue would
 * silently misroute half of every event type.
 *
 * Neither method is @Transactional. The service methods manage their own persistence, and keeping
 * these outside any transaction means the fail-soft catch is not swallowing an exception on a
 * transaction that has already been marked rollback-only.
 */
@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final NotificationService notificationService;

    public NotificationEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * The parameter is the concrete event type on purpose: Spring AMQP's default
     * TypePrecedence.INFERRED resolves the payload from this signature, so the sender's __TypeId__
     * header (which names recommendation-service's package) is never read. Widening this to Object
     * or Message would fall back to that header and blow up with ClassNotFoundException.
     */
    @RabbitListener(queues = NotificationConstants.QUEUE_NAME)
    public void onRecommendationGenerated(RecommendationGeneratedEvent event) {
        try {
            // recommendationId is guarded because it is half the idempotency key; the two nullable
            // display fields (topCareerName, matchPercentage) are tolerated and null-coalesced
            // downstream rather than rejected -- a recommendation with no career name is still
            // worth telling the student about.
            if (event == null || event.getUserId() == null || event.getRecommendationId() == null) {
                log.warn("Ignoring incomplete {} payload: {}",
                        NotificationConstants.ROUTING_KEY_RECOMMENDATION_GENERATED, event);
                return;
            }

            notificationService.processRecommendationNotification(event);
        } catch (Exception ex) {
            // Fail-soft: rethrowing would requeue the message and spin the listener forever on a
            // payload that will never succeed. A missed notification is recoverable; an infinite
            // redelivery loop takes the consumer down for every other student too.
            log.error("Failed to handle {} for recommendationId={}: {}",
                    NotificationConstants.ROUTING_KEY_RECOMMENDATION_GENERATED,
                    event == null ? null : event.getRecommendationId(),
                    ex.getMessage());
        }
    }

    /**
     * Harvests contact details only -- no notification is created. Without this, no
     * recommendation email could ever be addressed, since RecommendationGeneratedEvent carries
     * only a userId.
     */
    @RabbitListener(queues = NotificationConstants.STUDENT_QUEUE_NAME)
    public void onStudentRegistered(StudentRegisteredEvent event) {
        try {
            // email is guarded as well as userId: a contact row with a null email is worse than
            // no row at all, because UserContact.email is NOT NULL and the insert would fail on
            // every redelivery.
            if (event == null || event.getUserId() == null || event.getEmail() == null) {
                log.warn("Ignoring incomplete {} payload: {}",
                        NotificationConstants.ROUTING_KEY_STUDENT_REGISTERED, event);
                return;
            }

            notificationService.upsertContact(event);
        } catch (Exception ex) {
            log.error("Failed to handle {} for userId={}: {}",
                    NotificationConstants.ROUTING_KEY_STUDENT_REGISTERED,
                    event == null ? null : event.getUserId(),
                    ex.getMessage());
        }
    }

    /**
     * A third queue, not a second listener on either queue above -- see RabbitMQConfig for why.
     * paymentId is guarded because it is what the internal invoice-download call is keyed on; a
     * payment.service.url outage inside processSubscriptionInvoice is handled there (fail-soft,
     * email still sent without an attachment), not here.
     */
    @RabbitListener(queues = NotificationConstants.SUBSCRIPTION_QUEUE_NAME)
    public void onSubscriptionActivated(SubscriptionActivatedEvent event) {
        try {
            if (event == null || event.getUserId() == null || event.getPaymentId() == null) {
                log.warn("Ignoring incomplete {} payload: {}",
                        NotificationConstants.ROUTING_KEY_SUBSCRIPTION_ACTIVATED, event);
                return;
            }

            notificationService.processSubscriptionInvoice(event);
        } catch (Exception ex) {
            log.error("Failed to handle {} for paymentId={}: {}",
                    NotificationConstants.ROUTING_KEY_SUBSCRIPTION_ACTIVATED,
                    event == null ? null : event.getPaymentId(),
                    ex.getMessage());
        }
    }
}
