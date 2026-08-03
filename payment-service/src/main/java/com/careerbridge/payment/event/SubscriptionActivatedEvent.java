package com.careerbridge.payment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Published on careerbridge.exchange with routing key subscription.activated. auth-service consumes
 * it on careerbridge.auth.subscription.queue and copies both values onto the User row.
 *
 * validUntil is an ABSOLUTE timestamp, deliberately, not a duration. RabbitMQ is at-least-once, so
 * a consumer that received "30 days" and added it to the current expiry would silently double the
 * subscription on a redelivery. All date arithmetic happens here, in the service that owns the
 * rows; the consumer only ever assigns.
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
