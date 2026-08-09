package com.careerbridge.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Consumer-side copy of the payload payment-service publishes on a successful subscription
 * (exchange careerbridge.exchange, routing key subscription.activated). No shared module in this
 * project; every consumer keeps its own copy, and Spring AMQP's default TypePrecedence.INFERRED
 * resolves the payload from the listener's parameter type, so the differing package FQN needs no
 * type-mapper configuration.
 *
 * userRole is carried so the internal invoice-download call can pass PaymentServiceImpl's
 * ownership check with the caller's real role -- no privilege elevation, unlike
 * recruiter-service's PrsServiceClient.
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

    private Long paymentId;
    private String invoiceNumber;
    private BigDecimal amount;
    private String currency;
    private String billingCycle;
    private String userRole;
}
