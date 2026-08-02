package com.careerbridge.auth.consumer;

import com.careerbridge.auth.config.RabbitMQConfig;
import com.careerbridge.auth.event.SubscriptionActivatedEvent;
import com.careerbridge.auth.service.SubscriptionSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * auth-service's first and only inbound event.
 *
 * The parameter type must stay the concrete SubscriptionActivatedEvent. Spring AMQP's default
 * TypePrecedence.INFERRED resolves the payload from this method signature, which is the only
 * reason the differing package FQN between payment-service and this service works with zero
 * type-mapper configuration. Widening it to Object or Message falls back to the sender's
 * __TypeId__ header and dies with ClassNotFoundException.
 *
 * Fail-soft and deliberately not @Transactional: rethrowing would requeue the message and spin the
 * listener forever on a payload this service can never process. The subscriptions table in
 * payment-service remains authoritative, so a dropped event costs a stale plan claim, not a lost
 * subscription -- and a re-verify replays it.
 *
 * Do NOT add a second @RabbitListener to this queue. Two containers on one queue make RabbitMQ
 * round-robin between them, so roughly half of each event type would be bound into the wrong
 * class; notification-service has that lesson written down at length.
 */
@Component
public class SubscriptionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventConsumer.class);

    private final SubscriptionSyncService subscriptionSyncService;

    public SubscriptionEventConsumer(SubscriptionSyncService subscriptionSyncService) {
        this.subscriptionSyncService = subscriptionSyncService;
    }

    @RabbitListener(queues = RabbitMQConfig.SUBSCRIPTION_QUEUE)
    public void onSubscriptionActivated(SubscriptionActivatedEvent event) {
        try {
            if (event == null) {
                log.warn("Received a null subscription.activated payload");
                return;
            }
            log.info("Received subscription.activated: userId={}, plan={}",
                    event.getUserId(), event.getPlanName());
            subscriptionSyncService.applyActivation(event);
        } catch (Exception ex) {
            log.error("Failed to process subscription.activated for userId={}: {}",
                    event == null ? null : event.getUserId(), ex.getMessage());
        }
    }
}
