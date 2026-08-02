package com.careerbridge.auth.service;

import com.careerbridge.auth.event.SubscriptionActivatedEvent;
import com.careerbridge.auth.model.User;
import com.careerbridge.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Copies payment-service's subscription decision onto the User row, so the plan can be carried as a
 * JWT claim and injected downstream as X-User-Plan.
 *
 * The User row is a denormalised CACHE, not the source of truth. payment-service owns the
 * subscriptions table; if the two ever disagree, payment-service is right and this row is stale.
 *
 * A concrete @Service with no interface, matching notification-service's EmailService: one
 * implementation, and Mockito mocks classes fine. Kept separate from the consumer so the business
 * logic is testable without an AMQP payload, the same split every other consumer in this project
 * uses.
 */
@Service
public class SubscriptionSyncService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionSyncService.class);

    private final UserRepository userRepository;

    public SubscriptionSyncService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * SETS both fields from the event. It must never compute an offset from the current value --
     * RabbitMQ is at-least-once, and `expiry.plusDays(30)` on a redelivery would hand out a second
     * month for a single payment. All date arithmetic lives in payment-service, which owns the rows.
     *
     * findByIdAndIsDeletedFalse rather than findById: a soft-deleted account should not have its
     * plan updated, and this service's own repository already offers the filtered finder.
     */
    @Transactional
    public void applyActivation(SubscriptionActivatedEvent event) {
        if (event == null || event.getUserId() == null) {
            log.warn("Ignoring subscription.activated with no userId");
            return;
        }

        User user = userRepository.findByIdAndIsDeletedFalse(event.getUserId()).orElse(null);
        if (user == null) {
            // Not an error: the account may have been deleted between paying and delivery.
            log.warn("subscription.activated for unknown or deleted userId={}", event.getUserId());
            return;
        }

        user.setSubscriptionPlan(event.getPlanName());
        user.setSubscriptionExpiry(event.getValidUntil());
        userRepository.save(user);

        log.info("Updated subscription for userId={}: plan={}, validUntil={}",
                event.getUserId(), event.getPlanName(), event.getValidUntil());
    }
}
