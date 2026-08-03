package com.careerbridge.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A student who has never paid has no Subscription row at all, and getMySubscription synthesises a
 * FREE response for them rather than 404ing. In that case id, startDate and endDate are null --
 * `active` is still true, because the free tier never expires.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {

    private Long id;
    private Long userId;
    private String planName;
    private String planDescription;
    private BigDecimal planPrice;
    private String billingCycle;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String status;

    /**
     * Boxed Boolean, never a primitive: Lombok emits isActive() for a primitive boolean, which
     * collapses the JSON key to "active" and silently breaks the frontend contract. Same trap
     * notification-service documents on NotificationDocument.isRead.
     */
    private Boolean active;
}
