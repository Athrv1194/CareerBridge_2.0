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
 * One row per Razorpay order. Created PENDING before the student opens the checkout modal, flipped
 * to SUCCESS by a verified signature (or by reconcile), or to FAILED by a rejected one.
 */
@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    /**
     * Razorpay's own order id ("order_xxx"). NOT NULL because the row is only written after
     * Razorpay has answered -- see createOrder, which calls Razorpay first and persists second
     * precisely so this column can never be null.
     *
     * UNIQUE, and that matters twice: it is the lookup key for /verify, and it stops a replayed
     * create from producing two local rows for one Razorpay order. It is NOT sufficient on its own
     * to prevent a double subscription from two concurrent /verify calls -- see the pessimistic
     * lock on PaymentRepository.findByRazorpayOrderId and the unique payment_id on Subscription.
     */
    @Column(nullable = false, unique = true)
    private String razorpayOrderId;

    /** Razorpay's payment id ("pay_xxx"). Null until the student actually pays. */
    private String razorpayPaymentId;

    /**
     * Integer paise, exactly as Razorpay holds it -- NOT rupees, and deliberately not BigDecimal.
     * This is the amount that actually moved, so storing the integer Razorpay stores means a
     * dispute reconciles byte for byte with no conversion anywhere in the payment path. The
     * rupee value lives on SubscriptionPlan.price, which is what the pricing page renders.
     */
    @Column(nullable = false)
    private Long amountPaise;

    @Builder.Default
    @Column(nullable = false)
    private String currency = "INR";

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
