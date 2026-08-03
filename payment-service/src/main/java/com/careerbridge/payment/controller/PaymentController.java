package com.careerbridge.payment.controller;

import com.careerbridge.payment.constants.PaymentConstants;
import com.careerbridge.payment.dto.CreateOrderRequest;
import com.careerbridge.payment.dto.CreateOrderResponse;
import com.careerbridge.payment.dto.PaymentResponse;
import com.careerbridge.payment.dto.PaymentVerifyResponse;
import com.careerbridge.payment.dto.PlanResponse;
import com.careerbridge.payment.dto.SubscriptionResponse;
import com.careerbridge.payment.dto.VerifyPaymentRequest;
import com.careerbridge.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * /api/payment, singular, matching the gateway predicate -- gateway predicates match whole
 * segments, so a plural mapping would 404 every request arriving through it.
 *
 * No RBAC here. Every authorisation decision lives in the service layer, as in every other service
 * in this project. X-User-Org-Id is not read at all: subscriptions are per user, not per tenant,
 * so accepting an org header this service ignores would be misleading.
 */
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * PUBLIC -- the only endpoint in this service listed in the gateway's public-paths, because the
     * pricing page is shown to visitors before login. It deliberately reads NO headers: the gateway
     * forwards a public path with identityHeaders(null, null, null), so a method requiring
     * X-User-Id here would 400 on every call rather than being usefully public.
     */
    @GetMapping("/plans")
    public ResponseEntity<List<PlanResponse>> getPlans() {
        return ResponseEntity.ok(paymentService.getActivePlans());
    }

    @PostMapping("/orders")
    public ResponseEntity<CreateOrderResponse> createOrder(
            @RequestHeader(PaymentConstants.USER_ID_HEADER) Long userId,
            @RequestHeader(PaymentConstants.USER_ROLE_HEADER) String role,
            @RequestBody @Valid CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createOrder(role, userId, request));
    }

    @PostMapping("/verify")
    public ResponseEntity<PaymentVerifyResponse> verifyPayment(
            @RequestHeader(PaymentConstants.USER_ID_HEADER) Long userId,
            @RequestHeader(PaymentConstants.USER_ROLE_HEADER) String role,
            @RequestBody @Valid VerifyPaymentRequest request) {
        return ResponseEntity.ok(paymentService.verifyPayment(role, userId, request));
    }

    /**
     * The abandoned-checkout escape hatch: asks Razorpay directly whether an order was paid, for
     * the case where the browser closed before the verify callback fired.
     */
    @PostMapping("/orders/{razorpayOrderId}/reconcile")
    public ResponseEntity<PaymentVerifyResponse> reconcileOrder(
            @PathVariable String razorpayOrderId,
            @RequestHeader(PaymentConstants.USER_ID_HEADER) Long userId,
            @RequestHeader(PaymentConstants.USER_ROLE_HEADER) String role) {
        return ResponseEntity.ok(paymentService.reconcileOrder(role, userId, razorpayOrderId));
    }

    @GetMapping("/subscription/my")
    public ResponseEntity<SubscriptionResponse> getMySubscription(
            @RequestHeader(PaymentConstants.USER_ID_HEADER) Long userId,
            @RequestHeader(PaymentConstants.USER_ROLE_HEADER) String role) {
        return ResponseEntity.ok(paymentService.getMySubscription(role, userId));
    }

    @GetMapping("/payments/my")
    public ResponseEntity<List<PaymentResponse>> getMyPayments(
            @RequestHeader(PaymentConstants.USER_ID_HEADER) Long userId,
            @RequestHeader(PaymentConstants.USER_ROLE_HEADER) String role) {
        return ResponseEntity.ok(paymentService.getMyPayments(role, userId));
    }

    @GetMapping("/admin/subscriptions")
    public ResponseEntity<List<SubscriptionResponse>> getAllSubscriptions(
            @RequestHeader(PaymentConstants.USER_ROLE_HEADER) String role) {
        return ResponseEntity.ok(paymentService.getAllSubscriptions(role));
    }
}
