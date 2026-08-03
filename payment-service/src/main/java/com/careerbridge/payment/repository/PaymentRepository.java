package com.careerbridge.payment.repository;

import com.careerbridge.payment.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * PESSIMISTIC_WRITE, i.e. SELECT ... FOR UPDATE, and it is load-bearing.
     *
     * Without it, two concurrent POST /verify calls for the same order both read status=PENDING,
     * both verify the same genuinely-valid signature, both flip the row to SUCCESS and both insert
     * a Subscription. The UNIQUE constraint on razorpay_order_id does not help -- neither caller is
     * inserting a Payment. With the lock the loser blocks, then re-reads SUCCESS and takes the
     * idempotent branch. The unique payment_id on Subscription is the belt to this braces.
     *
     * Optional is legal because razorpay_order_id is UNIQUE.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    /** No lock: read-only path for the payment history endpoint. */
    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);
}
