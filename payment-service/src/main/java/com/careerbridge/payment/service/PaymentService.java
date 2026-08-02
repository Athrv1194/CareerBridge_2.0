package com.careerbridge.payment.service;

import com.careerbridge.payment.dto.CreateOrderRequest;
import com.careerbridge.payment.dto.CreateOrderResponse;
import com.careerbridge.payment.dto.PaymentResponse;
import com.careerbridge.payment.dto.PaymentVerifyResponse;
import com.careerbridge.payment.dto.PlanResponse;
import com.careerbridge.payment.dto.SubscriptionResponse;
import com.careerbridge.payment.dto.VerifyPaymentRequest;

import java.util.List;

public interface PaymentService {

    /** Public: the pricing page is shown to visitors before login. Takes no identity. */
    List<PlanResponse> getActivePlans();

    CreateOrderResponse createOrder(String callerRole, Long userId, CreateOrderRequest request);

    PaymentVerifyResponse verifyPayment(String callerRole, Long userId, VerifyPaymentRequest request);

    /** Asks Razorpay directly whether an abandoned order was actually paid. */
    PaymentVerifyResponse reconcileOrder(String callerRole, Long userId, String razorpayOrderId);

    SubscriptionResponse getMySubscription(String callerRole, Long userId);

    List<PaymentResponse> getMyPayments(String callerRole, Long userId);

    List<SubscriptionResponse> getAllSubscriptions(String callerRole);
}
