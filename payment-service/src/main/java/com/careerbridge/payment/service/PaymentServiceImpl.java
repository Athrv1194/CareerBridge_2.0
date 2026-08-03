package com.careerbridge.payment.service;

import com.careerbridge.payment.constants.PaymentConstants;
import com.careerbridge.payment.data.PlanDataSeeder;
import com.careerbridge.payment.dto.CreateOrderRequest;
import com.careerbridge.payment.dto.CreateOrderResponse;
import com.careerbridge.payment.dto.PaymentResponse;
import com.careerbridge.payment.dto.PaymentVerifyResponse;
import com.careerbridge.payment.dto.PlanResponse;
import com.careerbridge.payment.dto.SubscriptionResponse;
import com.careerbridge.payment.dto.VerifyPaymentRequest;
import com.careerbridge.payment.dto.external.RazorpayOrderDtos.OrderResponse;
import com.careerbridge.payment.event.SubscriptionActivatedEvent;
import com.careerbridge.payment.event.SubscriptionCommittedEvent;
import com.careerbridge.payment.exception.CustomException;
import com.careerbridge.payment.model.Payment;
import com.careerbridge.payment.model.PaymentStatus;
import com.careerbridge.payment.model.Subscription;
import com.careerbridge.payment.model.SubscriptionPlan;
import com.careerbridge.payment.model.SubscriptionStatus;
import com.careerbridge.payment.repository.PaymentRepository;
import com.careerbridge.payment.repository.SubscriptionPlanRepository;
import com.careerbridge.payment.repository.SubscriptionRepository;
import com.careerbridge.payment.util.Money;
import com.careerbridge.payment.util.RazorpaySignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Razorpay-backed subscription billing.
 *
 * Known gap, deliberate: there is no webhook. Razorpay Standard Checkout auto-captures, so if the
 * browser closes between paying and calling /verify, the money is taken and the Payment row sits
 * PENDING forever. reconcileOrder is the mitigation -- it asks Razorpay directly -- but it is pull,
 * not push, so somebody has to call it. A real deployment wants a webhook endpoint, which needs a
 * publicly reachable URL this environment does not have.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private static final int DAYS_MONTHLY = 30;
    private static final int DAYS_SEMESTER = 180;
    private static final int DAYS_ANNUAL = 365;
    private static final int DAYS_LIFETIME = 36500;

    private final SubscriptionPlanRepository planRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RazorpayClient razorpayClient;
    private final ApplicationEventPublisher applicationEventPublisher;

    private final String keySecret;

    public PaymentServiceImpl(SubscriptionPlanRepository planRepository,
                              PaymentRepository paymentRepository,
                              SubscriptionRepository subscriptionRepository,
                              RazorpayClient razorpayClient,
                              ApplicationEventPublisher applicationEventPublisher,
                              @Value("${razorpay.key.secret:}") String keySecret) {
        this.planRepository = planRepository;
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.razorpayClient = razorpayClient;
        this.applicationEventPublisher = applicationEventPublisher;
        this.keySecret = keySecret;
    }

    // ---------------------------------------------------------------------------------------
    // Plans
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> getActivePlans() {
        return planRepository.findByIsActiveTrueOrderByPriceAsc().stream()
                .map(this::toPlanResponse)
                .toList();
    }

    // ---------------------------------------------------------------------------------------
    // Order creation
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public CreateOrderResponse createOrder(String callerRole, Long userId, CreateOrderRequest request) {
        requireSubscriber(callerRole);

        SubscriptionPlan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> new CustomException("Plan not found", HttpStatus.NOT_FOUND));

        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new CustomException("That plan is no longer available", HttpStatus.BAD_REQUEST);
        }

        long amountPaise = Money.toPaise(plan.getPrice());

        // The free tier is catalog-only: it exists so the pricing page can display it, and every
        // account already has it by default (getMySubscription synthesises it when no row exists).
        // Charging zero through Razorpay is impossible anyway -- its minimum is one rupee -- and
        // writing a SUCCESS Payment row for it would need a synthetic razorpay_order_id for a
        // payment that never happened.
        if (amountPaise == 0L) {
            throw new CustomException("The free plan requires no purchase", HttpStatus.BAD_REQUEST);
        }

        if (amountPaise < Money.MINIMUM_CHARGEABLE_PAISE) {
            // Only reachable from a misconfigured catalog row, but Razorpay would reject it with an
            // opaque error and this is a clearer place to fail.
            throw new CustomException("Plan price is below the minimum chargeable amount",
                    HttpStatus.BAD_REQUEST);
        }

        // Razorpay FIRST, persist SECOND. The reverse would write a PENDING row with a null
        // razorpay_order_id, which the NOT NULL column rejects anyway, and would leave orphan rows
        // behind every failed call. The residual risk is the opposite orphan -- a read timeout can
        // leave an order at Razorpay with no local row -- which is harmless: nothing was charged
        // against it and the student simply retries.
        OrderResponse razorpayOrder = razorpayClient.createOrder(
                amountPaise, plan.getCurrency(), buildReceipt(userId));

        Payment payment = paymentRepository.save(Payment.builder()
                .userId(userId)
                .plan(plan)
                .razorpayOrderId(razorpayOrder.id())
                .amountPaise(amountPaise)
                .currency(plan.getCurrency())
                .status(PaymentStatus.PENDING)
                .build());

        log.info("Created payment {} for userId={} plan={} order={}",
                payment.getId(), userId, plan.getPlanName(), razorpayOrder.id());

        return CreateOrderResponse.builder()
                .paymentId(payment.getId())
                .razorpayOrderId(razorpayOrder.id())
                .amountPaise(amountPaise)
                .amount(plan.getPrice())
                .currency(plan.getCurrency())
                .keyId(razorpayClient.getKeyId())
                .planName(plan.getPlanName())
                .planDescription(plan.getDescription())
                .build();
    }

    /** Razorpay caps receipt at 40 characters; this is comfortably inside that. */
    private String buildReceipt(Long userId) {
        String receipt = "CB-" + userId + "-" + System.currentTimeMillis();
        return receipt.length() <= PaymentConstants.RECEIPT_MAX_LENGTH
                ? receipt
                : receipt.substring(0, PaymentConstants.RECEIPT_MAX_LENGTH);
    }

    // ---------------------------------------------------------------------------------------
    // Verification
    // ---------------------------------------------------------------------------------------

    /**
     * noRollbackFor = CustomException.class is required here, not decorative.
     *
     * Caught live, not by any unit test: Spring's default rollback rule reverts a transaction on
     * any unchecked exception, including CustomException. Without this, the invalid-signature
     * branch below writes status=FAILED and saves it, then throws CustomException(400) to report
     * the rejection to the caller -- and that throw silently rolled the FAILED write back,
     * leaving the payment stuck at PENDING forever with no record that a forged signature was
     * ever attempted. A Mockito-based test cannot see this: with no real Spring transaction proxy
     * in play, verify(paymentRepository).save(...) passes whether or not a surrounding
     * transaction would have reverted it. Confirmed against a live sandbox payment: before this
     * fix, a forged POST /verify correctly returned 400 while the payment row silently stayed
     * PENDING; after it, the row reads FAILED as intended.
     */
    @Override
    @Transactional(noRollbackFor = CustomException.class)
    public PaymentVerifyResponse verifyPayment(String callerRole, Long userId,
                                                VerifyPaymentRequest request) {
        requireSubscriber(callerRole);

        // Pessimistic lock: two concurrent /verify calls for one order would otherwise both read
        // PENDING, both verify the same valid signature and both insert a Subscription.
        Payment payment = loadOwnedPayment(request.getRazorpayOrderId(), userId);

        PaymentVerifyResponse alreadyDone = completedResponseOrNull(payment);
        if (alreadyDone != null) {
            return alreadyDone;
        }

        boolean valid = RazorpaySignatureVerifier.matches(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature(),
                keySecret);

        if (!valid) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.warn("Signature verification failed for order={} userId={}",
                    request.getRazorpayOrderId(), userId);
            throw new CustomException("Payment verification failed - invalid signature",
                    HttpStatus.BAD_REQUEST);
        }

        payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
        return completeSubscription(payment);
    }

    @Override
    @Transactional
    public PaymentVerifyResponse reconcileOrder(String callerRole, Long userId, String razorpayOrderId) {
        requireSubscriber(callerRole);

        Payment payment = loadOwnedPayment(razorpayOrderId, userId);

        PaymentVerifyResponse alreadyDone = completedResponseOrNull(payment);
        if (alreadyDone != null) {
            return alreadyDone;
        }

        // No signature here, and that is correct rather than a shortcut: the signature exists only
        // because the browser is untrusted. This is an authenticated server-to-server call to
        // Razorpay itself, so Razorpay's own answer IS the authentication.
        OrderResponse order = razorpayClient.fetchOrder(razorpayOrderId);
        if (order == null || !order.isPaid()) {
            throw new CustomException(
                    "Razorpay has not recorded this order as paid", HttpStatus.CONFLICT);
        }

        log.info("Reconciled abandoned order={} for userId={} -- Razorpay reports paid",
                razorpayOrderId, userId);
        return completeSubscription(payment);
    }

    /**
     * The single place a plan is granted. Both verifyPayment and reconcileOrder route through it,
     * so there is one implementation of "what a successful payment does", not two that can drift.
     */
    private PaymentVerifyResponse completeSubscription(Payment payment) {
        LocalDateTime now = LocalDateTime.now();
        SubscriptionPlan plan = payment.getPlan();

        payment.setStatus(PaymentStatus.SUCCESS);
        if (payment.getPaidAt() == null) {
            payment.setPaidAt(now);
        }
        paymentRepository.save(payment);

        // Renewals stack rather than reset: if the student still has time left, the new period
        // starts from the old endDate. Resetting to now would silently confiscate the remainder
        // from anyone who renews early.
        List<Subscription> existingActive = subscriptionRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(payment.getUserId(), SubscriptionStatus.ACTIVE);

        LocalDateTime base = existingActive.stream()
                .map(Subscription::getEndDate)
                .filter(end -> end.isAfter(now))
                .max(LocalDateTime::compareTo)
                .orElse(now);

        existingActive.forEach(subscription -> {
            subscription.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(subscription);
        });

        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .userId(payment.getUserId())
                .plan(plan)
                .paymentId(payment.getId())
                .startDate(now)
                .endDate(base.plusDays(billingDays(plan.getBillingCycle())))
                .status(SubscriptionStatus.ACTIVE)
                .build());

        // In-process event; PaymentEventPublisher turns it into a broker message only after this
        // transaction commits. See that class for why this one publishes after commit while every
        // other publisher in the project publishes inside the transaction.
        applicationEventPublisher.publishEvent(new SubscriptionCommittedEvent(
                SubscriptionActivatedEvent.builder()
                        .userId(payment.getUserId())
                        .planName(plan.getPlanName())
                        .validUntil(subscription.getEndDate())
                        .activatedAt(now)
                        .build()));

        log.info("Activated subscription {} for userId={} plan={} until={}",
                subscription.getId(), payment.getUserId(), plan.getPlanName(), subscription.getEndDate());

        return PaymentVerifyResponse.builder()
                .subscriptionId(subscription.getId())
                .planName(plan.getPlanName())
                .validUntil(subscription.getEndDate())
                .message("Subscription activated")
                .build();
    }

    /**
     * Idempotency, with a self-healing branch.
     *
     * Returns non-null only when the payment is already SUCCESS AND its subscription exists.
     * A SUCCESS payment with no subscription is a genuinely reachable state -- the process can die
     * between the status flip and the insert -- and short-circuiting on the status alone would
     * make that permanent, leaving a student who paid with nothing. In that case this returns null
     * so the caller falls through to completeSubscription and repairs it.
     */
    private PaymentVerifyResponse completedResponseOrNull(Payment payment) {
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            return null;
        }
        Optional<Subscription> existing = subscriptionRepository.findByPaymentId(payment.getId());
        if (existing.isEmpty()) {
            log.warn("Payment {} is SUCCESS with no subscription -- self-healing", payment.getId());
            return null;
        }
        Subscription subscription = existing.get();
        return PaymentVerifyResponse.builder()
                .subscriptionId(subscription.getId())
                .planName(subscription.getPlan().getPlanName())
                .validUntil(subscription.getEndDate())
                .message("Subscription already active")
                .build();
    }

    private Payment loadOwnedPayment(String razorpayOrderId, Long userId) {
        Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new CustomException("Payment not found", HttpStatus.NOT_FOUND));

        // Objects.equals, never ==: both sides are boxed Long well outside the Integer cache, so
        // == is false for exactly the ids this check exists to allow. 404 rather than 403, because
        // a student has no legitimate reason to address another student's order and 403 would
        // confirm it exists -- same shape as resume-service's resume lookups.
        if (!Objects.equals(payment.getUserId(), userId)) {
            throw new CustomException("Payment not found", HttpStatus.NOT_FOUND);
        }
        return payment;
    }

    private static int billingDays(String billingCycle) {
        return switch (billingCycle) {
            case PlanDataSeeder.CYCLE_MONTHLY -> DAYS_MONTHLY;
            case PlanDataSeeder.CYCLE_SEMESTER -> DAYS_SEMESTER;
            case PlanDataSeeder.CYCLE_ANNUAL -> DAYS_ANNUAL;
            case PlanDataSeeder.CYCLE_LIFETIME -> DAYS_LIFETIME;
            default -> throw new CustomException(
                    "Unknown billing cycle: " + billingCycle, HttpStatus.INTERNAL_SERVER_ERROR);
        };
    }

    // ---------------------------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------------------------

    /**
     * NOT readOnly, and that is load-bearing. This method flips an elapsed subscription to EXPIRED,
     * and Spring puts the Hibernate session in FlushMode.MANUAL for a read-only transaction -- the
     * dirty-checked change would be silently discarded with no error and no log, and a mocked unit
     * test would still pass because a mock never flushes.
     */
    @Override
    @Transactional
    public SubscriptionResponse getMySubscription(String callerRole, Long userId) {
        requireSubscriber(callerRole);

        List<Subscription> active = subscriptionRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, SubscriptionStatus.ACTIVE);

        if (active.isEmpty()) {
            return freePlanResponse(userId);
        }

        // Element 0 is the newest by the DESC ordering. The finder returns a List because nothing
        // in the schema enforces one ACTIVE row per user -- see SubscriptionRepository.
        Subscription subscription = active.get(0);

        if (subscription.getEndDate().isBefore(LocalDateTime.now())) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(subscription);
            log.info("Subscription {} expired for userId={}", subscription.getId(), userId);
            return freePlanResponse(userId);
        }

        return toSubscriptionResponse(subscription, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getMyPayments(String callerRole, Long userId) {
        requireSubscriber(callerRole);
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toPaymentResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getAllSubscriptions(String callerRole) {
        if (!PaymentConstants.ADMIN_ROLES.contains(callerRole)) {
            throw new CustomException("Only ORG_ADMIN or SUPER_ADMIN may list all subscriptions",
                    HttpStatus.FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        return subscriptionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(subscription -> toSubscriptionResponse(subscription,
                        subscription.getStatus() == SubscriptionStatus.ACTIVE
                                && subscription.getEndDate().isAfter(now)))
                .toList();
    }

    // ---------------------------------------------------------------------------------------
    // Mapping and RBAC
    // ---------------------------------------------------------------------------------------

    private void requireSubscriber(String callerRole) {
        if (!PaymentConstants.SUBSCRIBER_ROLES.contains(callerRole)) {
            throw new CustomException("Only STUDENT or ORG_ADMIN may manage a subscription",
                    HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Synthesised, with no database row behind it. Every account is on the free tier until it pays,
     * so a student who has never subscribed gets this rather than a 404. planName is FREE, matching
     * the literal that auth-service's User.subscriptionPlan has defaulted to since day one.
     */
    private SubscriptionResponse freePlanResponse(Long userId) {
        SubscriptionPlan free = planRepository.findByPlanName(PlanDataSeeder.PLAN_FREE).orElse(null);
        return SubscriptionResponse.builder()
                .userId(userId)
                .planName(PlanDataSeeder.PLAN_FREE)
                .planDescription(free != null ? free.getDescription() : null)
                .planPrice(free != null ? free.getPrice() : BigDecimal.ZERO)
                .billingCycle(free != null ? free.getBillingCycle() : PlanDataSeeder.CYCLE_LIFETIME)
                .status(SubscriptionStatus.ACTIVE.name())
                .active(true)
                .build();
    }

    private SubscriptionResponse toSubscriptionResponse(Subscription subscription, boolean active) {
        SubscriptionPlan plan = subscription.getPlan();
        return SubscriptionResponse.builder()
                .id(subscription.getId())
                .userId(subscription.getUserId())
                .planName(plan.getPlanName())
                .planDescription(plan.getDescription())
                .planPrice(plan.getPrice())
                .billingCycle(plan.getBillingCycle())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .status(subscription.getStatus().name())
                .active(active)
                .build();
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .razorpayOrderId(payment.getRazorpayOrderId())
                .razorpayPaymentId(payment.getRazorpayPaymentId())
                .amountPaise(payment.getAmountPaise())
                .amount(Money.toRupees(payment.getAmountPaise()))
                .currency(payment.getCurrency())
                .status(payment.getStatus().name())
                .planName(payment.getPlan().getPlanName())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    /**
     * features is stored as a JSON array string. Parsed by hand rather than with an ObjectMapper:
     * the only writer is PlanDataSeeder, the contents are four hardcoded literal lists with no
     * quotes or backslashes, and a malformed value should degrade to an empty list rather than
     * 500 the public pricing page.
     */
    private PlanResponse toPlanResponse(SubscriptionPlan plan) {
        return PlanResponse.builder()
                .id(plan.getId())
                .planName(plan.getPlanName())
                .description(plan.getDescription())
                .price(plan.getPrice())
                .currency(plan.getCurrency())
                .billingCycle(plan.getBillingCycle())
                .features(parseFeatures(plan.getFeatures()))
                .build();
    }

    static List<String> parseFeatures(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return List.of();
        }
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(body.split(","))
                .map(String::trim)
                .map(token -> token.replaceAll("^\"|\"$", ""))
                .filter(token -> !token.isEmpty())
                .toList();
    }
}
