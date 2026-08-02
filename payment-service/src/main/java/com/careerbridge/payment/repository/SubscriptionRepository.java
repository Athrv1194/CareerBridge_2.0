package com.careerbridge.payment.repository;

import com.careerbridge.payment.model.Subscription;
import com.careerbridge.payment.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /**
     * Returns a List, NOT an Optional, and that is deliberate.
     *
     * Nothing in the schema enforces "at most one ACTIVE subscription per user" -- there is no
     * unique constraint on (user_id, status), because the obvious (user_id, plan_id) one breaks
     * renewals (see the class comment on Subscription). Two near-simultaneous verifications, or a
     * user holding both a student and a college plan, legitimately produce two ACTIVE rows, and an
     * Optional finder would then throw IncorrectResultSizeDataAccessException on a plain read of
     * the subscription page. A List degrades instead of breaking: reads take element 0, the newest
     * by the DESC ordering, and the next write cancels every row it finds.
     *
     * Identical reasoning to recommendation-service's findByUserIdAndIsActiveTrue and
     * roadmap-service's findByStudentIdOrderByStartedAtDesc. Do not "tidy" this to an Optional.
     */
    List<Subscription> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, SubscriptionStatus status);

    List<Subscription> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Optional is legal here, unlike the finder above: payment_id carries a real UNIQUE constraint.
     * Backs the self-healing branch of verifyPayment -- a payment can be SUCCESS with no
     * subscription if the process died between the status flip and the insert.
     */
    Optional<Subscription> findByPaymentId(Long paymentId);

    List<Subscription> findAllByOrderByCreatedAtDesc();
}
