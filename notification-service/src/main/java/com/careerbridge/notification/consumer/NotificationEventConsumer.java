package com.careerbridge.notification.consumer;

import com.careerbridge.notification.constants.NotificationConstants;
import com.careerbridge.notification.event.PasswordChangedEvent;
import com.careerbridge.notification.event.PasswordResetRequestedEvent;
import com.careerbridge.notification.event.RecommendationGeneratedEvent;
import com.careerbridge.notification.event.StudentRegisteredEvent;
import com.careerbridge.notification.service.EmailService;
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
    private final EmailService emailService;

    public NotificationEventConsumer(NotificationService notificationService, EmailService emailService) {
        this.notificationService = notificationService;
        this.emailService = emailService;
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
     * Straight to EmailService, not through NotificationService -- an OTP is not something that
     * belongs in the in-app notification feed or the NotificationRecord audit trail. There is
     * nothing durable worth writing about a code that is dead again in ten minutes either way.
     */
    @RabbitListener(queues = NotificationConstants.PASSWORD_RESET_QUEUE_NAME)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        try {
            if (event == null || event.getEmail() == null || event.getOtp() == null) {
                log.warn("Ignoring incomplete {} payload: {}",
                        NotificationConstants.ROUTING_KEY_PASSWORD_RESET_REQUESTED, event);
                return;
            }

            emailService.sendPasswordResetOtpEmail(
                    event.getEmail(), event.getFirstName(), event.getOtp(), event.getExpiresInMinutes());
        } catch (Exception ex) {
            log.error("Failed to handle {} for email={}: {}",
                    NotificationConstants.ROUTING_KEY_PASSWORD_RESET_REQUESTED,
                    event == null ? null : event.getEmail(),
                    ex.getMessage());
        }
    }

    @RabbitListener(queues = NotificationConstants.PASSWORD_CHANGED_QUEUE_NAME)
    public void onPasswordChanged(PasswordChangedEvent event) {
        try {
            if (event == null || event.getEmail() == null) {
                log.warn("Ignoring incomplete {} payload: {}",
                        NotificationConstants.ROUTING_KEY_PASSWORD_CHANGED, event);
                return;
            }

            emailService.sendPasswordChangedEmail(event.getEmail(), event.getFirstName());
        } catch (Exception ex) {
            log.error("Failed to handle {} for email={}: {}",
                    NotificationConstants.ROUTING_KEY_PASSWORD_CHANGED,
                    event == null ? null : event.getEmail(),
                    ex.getMessage());
        }
    }
}
