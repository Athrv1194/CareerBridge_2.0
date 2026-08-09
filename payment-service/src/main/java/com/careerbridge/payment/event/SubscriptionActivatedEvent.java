package com.careerbridge.payment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Published on careerbridge.exchange with routing key subscription.activated. auth-service consumes
 * it on careerbridge.auth.subscription.queue and copies userId/planName/validUntil onto the User
 * row; notification-service consumes it on its own queue to email an invoice.
 *
 * validUntil is an ABSOLUTE timestamp, deliberately, not a duration. RabbitMQ is at-least-once, so
 * a consumer that received "30 days" and added it to the current expiry would silently double the
 * subscription on a redelivery. All date arithmetic happens here, in the service that owns the
 * rows; a consumer only ever assigns.
 *
 * paymentId/invoiceNumber/amount/currency/billingCycle/userRole were added for the invoice email
 * feature. This is backward compatible with auth-service's older copy of this class: Jackson 3 has
 * FAIL_ON_UNKNOWN_PROPERTIES off, so a consumer that has not been updated simply ignores the new
 * fields rather than failing to deserialize. Adding fields is safe; renaming or removing one is
 * still breaking.
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

    /** Payment.id -- what notification-service's invoice download call is keyed on. */
    private Long paymentId;

    /** "CB-INV-000042", derived from paymentId. Carried on the event so it need not be recomputed. */
    private String invoiceNumber;

    /** Display rupees, from the immutable Payment.amountPaise -- never the mutable plan catalog price. */
    private BigDecimal amount;

    private String currency;
    private String billingCycle;

    /**
     * The caller's real role at the time of payment (STUDENT or ORG_ADMIN -- see
     * PaymentConstants.SUBSCRIBER_ROLES). Lets notification-service's internal invoice fetch pass
     * on plain ownership instead of elevating its own role, unlike recruiter-service's
     * PrsServiceClient, which legitimately elevates for a non-financial leaderboard read.
     */
    private String userRole;
}
