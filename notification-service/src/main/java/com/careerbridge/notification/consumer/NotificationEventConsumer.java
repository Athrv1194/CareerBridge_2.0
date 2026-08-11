package com.careerbridge.notification.consumer;

import com.careerbridge.notification.constants.NotificationConstants;
import com.careerbridge.notification.event.OrgAdminInvitedEvent;
import com.careerbridge.notification.event.PasswordChangedEvent;
import com.careerbridge.notification.event.PasswordResetRequestedEvent;
import com.careerbridge.notification.event.RecommendationGeneratedEvent;
import com.careerbridge.notification.event.SessionAcceptedEvent;
import com.careerbridge.notification.event.SessionBookedEvent;
import com.careerbridge.notification.event.SessionCompletedEvent;
import com.careerbridge.notification.event.StudentRegisteredEvent;
import com.careerbridge.notification.event.SubscriptionActivatedEvent;
import com.careerbridge.notification.service.EmailService;
import com.careerbridge.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Nine listeners on nine separate queues -- see RabbitMQConfig for why one shared queue would
 * silently misroute a proportion of every event type.
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

    /**
     * A sixth queue, not a second listener on any queue above -- see RabbitMQConfig for why. Straight
     * to EmailService, not through NotificationService: an admin invite is not something that belongs
     * in the in-app feed, and the recipient has no session yet to read a feed with anyway -- same
     * reasoning as onPasswordResetRequested.
     */
    @RabbitListener(queues = NotificationConstants.ORG_ADMIN_QUEUE_NAME)
    public void onOrgAdminInvited(OrgAdminInvitedEvent event) {
        try {
            if (event == null || event.getEmail() == null || event.getResetToken() == null) {
                log.warn("Ignoring incomplete {} payload: {}",
                        NotificationConstants.ROUTING_KEY_ORG_ADMIN_INVITED, event);
                return;
            }

            emailService.sendOrgAdminInviteEmail(event.getEmail(), event.getFirstName(),
                    event.getOrganizationName(), event.getResetToken(), event.getExpiresInHours());
        } catch (Exception ex) {
            log.error("Failed to handle {} for email={}: {}",
                    NotificationConstants.ROUTING_KEY_ORG_ADMIN_INVITED,
                    event == null ? null : event.getEmail(),
                    ex.getMessage());
        }
    }

    /**
     * Seventh queue. Routed through NotificationService rather than straight to EmailService,
     * unlike the password and invite handlers: a session request IS something that belongs in the
     * in-app feed, and the mentor already has an account to read it with.
     *
     * mentorUserId is guarded because it is the recipient here -- the only session event addressed
     * to the mentor rather than the student.
     */
    @RabbitListener(queues = NotificationConstants.SESSION_BOOKED_QUEUE_NAME)
    public void onSessionBooked(SessionBookedEvent event) {
        try {
            if (event == null || event.getMentorUserId() == null || event.getSessionId() == null) {
                log.warn("Ignoring incomplete {} payload: {}",
                        NotificationConstants.ROUTING_KEY_SESSION_BOOKED, event);
                return;
            }

            notificationService.processSessionBooked(event);
        } catch (Exception ex) {
            log.error("Failed to handle {} for sessionId={}: {}",
                    NotificationConstants.ROUTING_KEY_SESSION_BOOKED,
                    event == null ? null : event.getSessionId(),
                    ex.getMessage());
        }
    }

    /** Eighth queue. Recipient is the student; carries the meeting link. */
    @RabbitListener(queues = NotificationConstants.SESSION_ACCEPTED_QUEUE_NAME)
    public void onSessionAccepted(SessionAcceptedEvent event) {
        try {
            if (event == null || event.getStudentId() == null || event.getSessionId() == null) {
                log.warn("Ignoring incomplete {} payload: {}",
                        NotificationConstants.ROUTING_KEY_SESSION_ACCEPTED, event);
                return;
            }

            notificationService.processSessionAccepted(event);
        } catch (Exception ex) {
            log.error("Failed to handle {} for sessionId={}: {}",
                    NotificationConstants.ROUTING_KEY_SESSION_ACCEPTED,
                    event == null ? null : event.getSessionId(),
                    ex.getMessage());
        }
    }

    /**
     * Ninth queue. prs-service binds its own careerbridge.prs.mentor.queue to this same routing key
     * for the mentoring score -- two independent queues, so both receive every copy.
     */
    @RabbitListener(queues = NotificationConstants.SESSION_COMPLETED_QUEUE_NAME)
    public void onSessionCompleted(SessionCompletedEvent event) {
        try {
            if (event == null || event.getStudentId() == null || event.getSessionId() == null) {
                log.warn("Ignoring incomplete {} payload: {}",
                        NotificationConstants.ROUTING_KEY_SESSION_COMPLETED, event);
                return;
            }

            notificationService.processSessionCompleted(event);
        } catch (Exception ex) {
            log.error("Failed to handle {} for sessionId={}: {}",
                    NotificationConstants.ROUTING_KEY_SESSION_COMPLETED,
                    event == null ? null : event.getSessionId(),
                    ex.getMessage());
        }
    }
}
