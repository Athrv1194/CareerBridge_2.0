package com.careerbridge.payment.model;

/**
 * PENDING  -- a Razorpay order exists, the student has not completed (or we have not verified) it.
 * SUCCESS  -- signature verified, or Razorpay itself reported the order paid via reconcile.
 * FAILED   -- signature verification was attempted and rejected.
 *
 * A payment abandoned in the Razorpay modal stays PENDING forever unless someone calls reconcile;
 * there is no webhook in this deployment. See the class comment on PaymentServiceImpl.
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED
}
