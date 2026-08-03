package com.careerbridge.auth.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A local copy of payment-service's event of the same name. There is no shared module in this
 * project; every consumer keeps its own copy, and Spring AMQP's default TypePrecedence.INFERRED
 * resolves the payload from the listener's parameter type, so the differing package FQN between
 * publisher and consumer needs no type-mapper configuration.
 *
 * Adding a field on the publisher side stays backward compatible -- Jackson 3 has
 * FAIL_ON_UNKNOWN_PROPERTIES off. Renaming or removing one is still breaking.
 *
 * validUntil is an ABSOLUTE timestamp, not a duration, and the consumer assigns it verbatim.
 * RabbitMQ is at-least-once, so a consumer that added a duration to the current expiry would
 * silently double a student's subscription on a redelivery.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionActivatedEvent {

    private Long userId;
    private String planName;
    private LocalDateTime validUntil;
    private LocalDateTime activatedAt;
}
