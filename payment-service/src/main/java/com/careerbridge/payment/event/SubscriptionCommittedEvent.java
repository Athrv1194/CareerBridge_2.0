package com.careerbridge.payment.event;

/**
 * An in-process Spring application event, NOT a RabbitMQ message.
 *
 * PaymentServiceImpl publishes this inside its transaction; PaymentEventPublisher listens for it
 * with @TransactionalEventListener(AFTER_COMMIT) and only then sends the real
 * subscription.activated message to the broker. The indirection exists purely to move the broker
 * publish past the commit boundary -- see PaymentEventPublisher for why that matters here and
 * nowhere else in this project.
 */
public record SubscriptionCommittedEvent(SubscriptionActivatedEvent payload) {
}
