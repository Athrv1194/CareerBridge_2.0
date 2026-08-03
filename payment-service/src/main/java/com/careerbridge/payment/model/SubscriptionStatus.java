package com.careerbridge.payment.model;

/**
 * ACTIVE    -- current, and endDate is still in the future.
 * EXPIRED   -- endDate has passed. Set lazily by getMySubscription on the first read after expiry.
 * CANCELLED -- superseded by a newer subscription for the same student.
 */
public enum SubscriptionStatus {
    ACTIVE,
    EXPIRED,
    CANCELLED
}
