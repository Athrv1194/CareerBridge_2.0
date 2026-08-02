package com.careerbridge.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One row per successful purchase. Rows accumulate: a renewal does not update the old row, it
 * CANCELs it and inserts a new one, so the table is a complete billing history.
 *
 * There is deliberately NO unique constraint on (user_id, plan_id). The obvious one breaks the
 * commonest case in the system: a monthly subscriber renewing STUDENT_PREMIUM is the same user and
 * the same plan, so the second payment would die on DataIntegrityViolationException. Consequently
 * "one ACTIVE subscription per user" is a convention this service maintains, not something the
 * schema enforces -- which is exactly why SubscriptionRepository's finder returns a List and not an
 * Optional. Same rule, same reasoning, as recommendation-service's findByUserIdAndIsActiveTrue and
 * roadmap-service's findByStudentIdOrderByStartedAtDesc.
 */
@Entity
@Table(name = "subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    /**
     * The payment that bought this subscription. UNIQUE, and this is the real guard against two
     * concurrent /verify calls both inserting a subscription for one order -- the pessimistic lock
     * on the payment lookup is the fast path, this constraint is the guarantee. It also legitimises
     * the Optional return on findByPaymentId, per this project's rule that a single-result finder
     * needs a unique constraint behind the queried column.
     */
    @Column(nullable = false, unique = true)
    private Long paymentId;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    /** Stored, never acted on. There is no auto-renewal in this service. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean autoRenew = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
