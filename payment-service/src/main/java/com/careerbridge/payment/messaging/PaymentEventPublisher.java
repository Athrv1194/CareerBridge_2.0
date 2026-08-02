package com.careerbridge.payment.messaging;

import com.careerbridge.payment.config.RabbitMQConfig;
import com.careerbridge.payment.event.SubscriptionActivatedEvent;
import com.careerbridge.payment.event.SubscriptionCommittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes subscription.activated AFTER the database transaction commits, which is a deliberate
 * deviation from the other four publishers in this project.
 *
 * auth-service, prs-service, recruiter-service and resume-service all publish inside @Transactional
 * and carry a comment acknowledging the phantom-event window. That is an acceptable trade there:
 * a phantom recommendation.generated or resume.generated costs a lagging score or a missed
 * notification, and the next event repairs it.
 *
 * Here the blast radius is different in kind. A phantom subscription.activated makes auth-service
 * set subscriptionPlan = STUDENT_PREMIUM and subscriptionExpiry = +30 days for a payment whose
 * Payment and Subscription rows were rolled back and do not exist. The user gets a paid plan for
 * free, and there is no local row to reconcile against, so nothing ever detects or repairs it.
 *
 * The reverse failure is strictly safer: the commit succeeds, the broker is down, the event is
 * lost, and the student still has a real ACTIVE Subscription row that GET /subscription/my
 * reports correctly -- only the denormalised copy on the User row (and therefore the JWT claim)
 * lags until the next successful publish or a re-verify. The database is the source of truth.
 *
 * This listener MUST live on a bean separate from PaymentServiceImpl. Spring's transaction-bound
 * listeners are registered through the proxy, and a self-invoked call would bypass it entirely --
 * the same proxy trap documented on ai-coach-service's CatalogRefresher.
 *
 * Fail-soft: a broker outage must never fail a payment that already succeeded.
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubscriptionCommitted(SubscriptionCommittedEvent event) {
        publish(event.payload());
    }

    /**
     * Package-private and callable directly, for the reconcile path and for tests. The transaction
     * boundary is enforced by the listener above, not by this method.
     */
    void publish(SubscriptionActivatedEvent payload) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.SUBSCRIPTION_ACTIVATED_ROUTING_KEY,
                    payload);
            log.info("Published {} for userId={} plan={} validUntil={}",
                    RabbitMQConfig.SUBSCRIPTION_ACTIVATED_ROUTING_KEY,
                    payload.getUserId(), payload.getPlanName(), payload.getValidUntil());
        } catch (Exception ex) {
            log.warn("Failed to publish {} for userId={}: {}. The subscription row is committed and "
                            + "authoritative; auth-service will lag until the next successful publish.",
                    RabbitMQConfig.SUBSCRIPTION_ACTIVATED_ROUTING_KEY,
                    payload.getUserId(), ex.getMessage());
        }
    }
}
