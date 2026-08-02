package com.careerbridge.payment.service;

import com.careerbridge.payment.dto.PaymentVerifyResponse;
import com.careerbridge.payment.dto.SubscriptionResponse;
import com.careerbridge.payment.dto.VerifyPaymentRequest;
import com.careerbridge.payment.dto.external.RazorpayOrderDtos.OrderResponse;
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
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceVerifyTest {

    private static final String SECRET = "testsecret";
    private static final String ORDER_ID = "order_test123";
    private static final String PAYMENT_ID = "pay_test456";

    /** openssl: HMAC-SHA256("order_test123|pay_test456", "testsecret") -- see RazorpaySignatureVerifierTest. */
    private static final String VALID_SIGNATURE =
            "a14b537deda7f4f70d98f120b68fecda8d9b8530f5f5f2a4e65b66e391add2cc";

    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private RazorpayClient razorpayClient;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    private PaymentServiceImpl service() {
        return new PaymentServiceImpl(planRepository, paymentRepository, subscriptionRepository,
                razorpayClient, applicationEventPublisher, SECRET);
    }

    private static SubscriptionPlan monthlyPlan() {
        return SubscriptionPlan.builder()
                .id(2L).planName("STUDENT_PREMIUM").description("Premium")
                .price(new BigDecimal("199.00")).currency("INR").billingCycle("MONTHLY")
                .isActive(true).build();
    }

    private static Payment pendingPayment() {
        return Payment.builder()
                .id(77L).userId(21L).plan(monthlyPlan())
                .razorpayOrderId(ORDER_ID).amountPaise(19900L).currency("INR")
                .status(PaymentStatus.PENDING).build();
    }

    private static VerifyPaymentRequest validRequest() {
        return VerifyPaymentRequest.builder()
                .razorpayOrderId(ORDER_ID)
                .razorpayPaymentId(PAYMENT_ID)
                .razorpaySignature(VALID_SIGNATURE)
                .build();
    }

    private void stubSubscriptionSave() {
        when(subscriptionRepository.save(any())).thenAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(300L);
            }
            return s;
        });
    }

    // ----- happy path -----

    @Test
    void verifyPayment_ValidSignature_MarksPaymentSuccessAndSetsPaidAt() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(pendingPayment()));
        when(subscriptionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(21L, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubSubscriptionSave();

        service().verifyPayment("STUDENT", 21L, validRequest());

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals(PaymentStatus.SUCCESS, captor.getValue().getStatus());
        assertEquals(PAYMENT_ID, captor.getValue().getRazorpayPaymentId());
        assertNotNull(captor.getValue().getPaidAt());
    }

    @Test
    void verifyPayment_ValidSignature_CreatesActiveSubscriptionThirtyDaysOut() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(pendingPayment()));
        when(subscriptionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(21L, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubSubscriptionSave();

        PaymentVerifyResponse response = service().verifyPayment("STUDENT", 21L, validRequest());

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription saved = captor.getValue();
        assertEquals(SubscriptionStatus.ACTIVE, saved.getStatus());
        assertEquals(21L, saved.getUserId());
        assertEquals(77L, saved.getPaymentId());
        long days = ChronoUnit.DAYS.between(saved.getStartDate(), saved.getEndDate());
        assertEquals(30, days);
        assertEquals("STUDENT_PREMIUM", response.getPlanName());
    }

    @Test
    void verifyPayment_ValidSignature_PublishesSubscriptionCommittedEvent() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(pendingPayment()));
        when(subscriptionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(21L, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubSubscriptionSave();

        service().verifyPayment("STUDENT", 21L, validRequest());

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertTrue(captor.getValue() instanceof SubscriptionCommittedEvent);
        SubscriptionCommittedEvent event = (SubscriptionCommittedEvent) captor.getValue();
        assertEquals(21L, event.payload().getUserId());
        assertEquals("STUDENT_PREMIUM", event.payload().getPlanName());
        // validUntil is an absolute timestamp, never a duration -- a consumer that added a duration
        // would double the subscription on a RabbitMQ redelivery.
        assertNotNull(event.payload().getValidUntil());
    }

    @Test
    void verifyPayment_Renewal_StacksOnRemainingDaysInsteadOfResetting() {
        LocalDateTime existingEnd = LocalDateTime.now().plusDays(10);
        Subscription existing = Subscription.builder()
                .id(200L).userId(21L).plan(monthlyPlan()).paymentId(70L)
                .startDate(LocalDateTime.now().minusDays(20)).endDate(existingEnd)
                .status(SubscriptionStatus.ACTIVE).build();

        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(pendingPayment()));
        when(subscriptionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(21L, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of(existing));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubSubscriptionSave();

        service().verifyPayment("STUDENT", 21L, validRequest());

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository, times(2)).save(captor.capture());

        Subscription cancelled = captor.getAllValues().get(0);
        assertEquals(SubscriptionStatus.CANCELLED, cancelled.getStatus());

        Subscription created = captor.getAllValues().get(1);
        // The new period starts from the OLD endDate, not from now: 10 remaining days + 30 new.
        // Asserted as an exact offset from the existing end date so wall-clock drift between the
        // service's now() and the test's cannot make this flake.
        assertEquals(existingEnd.plusDays(30), created.getEndDate(),
                "an early renewal must stack on the remaining days, not reset to 30 from today");
        assertTrue(ChronoUnit.DAYS.between(created.getStartDate(), created.getEndDate()) >= 39,
                "the resulting subscription must cover roughly 40 days from today");
    }

    @Test
    void verifyPayment_ExpiredPreviousSubscription_DoesNotStack() {
        Subscription stale = Subscription.builder()
                .id(200L).userId(21L).plan(monthlyPlan()).paymentId(70L)
                .startDate(LocalDateTime.now().minusDays(60))
                .endDate(LocalDateTime.now().minusDays(5))
                .status(SubscriptionStatus.ACTIVE).build();

        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(pendingPayment()));
        when(subscriptionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(21L, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of(stale));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubSubscriptionSave();

        service().verifyPayment("STUDENT", 21L, validRequest());

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository, times(2)).save(captor.capture());
        Subscription created = captor.getAllValues().get(1);
        // Measured against the subscription's own startDate, not a fresh now(): the test's clock
        // reading is microseconds later than the service's, which truncates 30 days to 29.
        long days = ChronoUnit.DAYS.between(created.getStartDate(), created.getEndDate());
        assertEquals(30, days, "an already-elapsed subscription must not extend the new one");
    }

    // ----- invalid signature -----

    @Test
    void verifyPayment_InvalidSignature_Throws400() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(pendingPayment()));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomException ex = assertThrows(CustomException.class, () -> service().verifyPayment(
                "STUDENT", 21L, VerifyPaymentRequest.builder()
                        .razorpayOrderId(ORDER_ID).razorpayPaymentId(PAYMENT_ID)
                        .razorpaySignature("forged").build()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void verifyPayment_InvalidSignature_MarksPaymentFailedAndCreatesNoSubscription() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(pendingPayment()));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(CustomException.class, () -> service().verifyPayment(
                "STUDENT", 21L, VerifyPaymentRequest.builder()
                        .razorpayOrderId(ORDER_ID).razorpayPaymentId(PAYMENT_ID)
                        .razorpaySignature("forged").build()));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals(PaymentStatus.FAILED, captor.getValue().getStatus());
        verify(subscriptionRepository, never()).save(any());
        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    // ----- ownership and lookup -----

    @Test
    void verifyPayment_UnknownOrderId_Throws404() {
        when(paymentRepository.findByRazorpayOrderId("order_nope")).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(CustomException.class,
                () -> service().verifyPayment("STUDENT", 21L, VerifyPaymentRequest.builder()
                        .razorpayOrderId("order_nope").razorpayPaymentId(PAYMENT_ID)
                        .razorpaySignature(VALID_SIGNATURE).build())).getStatus());
    }

    @Test
    void verifyPayment_OrderBelongsToAnotherUser_Throws404Not403() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(pendingPayment()));

        // 404, deliberately: a 403 would confirm that someone else's order exists.
        CustomException ex = assertThrows(CustomException.class,
                () -> service().verifyPayment("STUDENT", 999L, validRequest()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void verifyPayment_RecruiterRole_Throws403() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(CustomException.class,
                () -> service().verifyPayment("RECRUITER", 21L, validRequest())).getStatus());
    }

    // ----- idempotency and self-healing -----

    @Test
    void verifyPayment_AlreadySuccessWithSubscription_ReturnsExistingWithoutRepublishing() {
        Payment done = pendingPayment();
        done.setStatus(PaymentStatus.SUCCESS);
        Subscription existing = Subscription.builder()
                .id(300L).userId(21L).plan(monthlyPlan()).paymentId(77L)
                .startDate(LocalDateTime.now()).endDate(LocalDateTime.now().plusDays(30))
                .status(SubscriptionStatus.ACTIVE).build();

        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(done));
        when(subscriptionRepository.findByPaymentId(77L)).thenReturn(Optional.of(existing));

        PaymentVerifyResponse response = service().verifyPayment("STUDENT", 21L, validRequest());

        assertEquals(300L, response.getSubscriptionId());
        verify(subscriptionRepository, never()).save(any());
        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void verifyPayment_AlreadySuccessButSubscriptionMissing_SelfHealsAndCreatesIt() {
        // Reachable: the process can die between the status flip and the subscription insert.
        // Short-circuiting on status alone would make that permanent -- the student paid for nothing.
        Payment done = pendingPayment();
        done.setStatus(PaymentStatus.SUCCESS);

        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(done));
        when(subscriptionRepository.findByPaymentId(77L)).thenReturn(Optional.empty());
        when(subscriptionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(21L, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubSubscriptionSave();

        PaymentVerifyResponse response = service().verifyPayment("STUDENT", 21L, validRequest());

        assertEquals(300L, response.getSubscriptionId());
        verify(subscriptionRepository).save(any());
        verify(applicationEventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void paymentRepository_FindByRazorpayOrderId_CarriesPessimisticWriteLock() throws Exception {
        // Two concurrent /verify calls for one order would otherwise both read PENDING, both verify
        // the same valid signature and both insert a Subscription. The lock is not observable from
        // a mocked test any other way.
        Method finder = PaymentRepository.class.getMethod("findByRazorpayOrderId", String.class);
        Lock lock = finder.getAnnotation(Lock.class);
        assertNotNull(lock, "findByRazorpayOrderId must carry @Lock");
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    // ----- reconcile -----

    @Test
    void reconcile_RazorpayReportsPaid_CompletesSubscriptionWithoutSignature() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(pendingPayment()));
        when(razorpayClient.fetchOrder(ORDER_ID))
                .thenReturn(new OrderResponse(ORDER_ID, 19900L, "INR", "paid", "r"));
        when(subscriptionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(21L, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubSubscriptionSave();

        PaymentVerifyResponse response = service().reconcileOrder("STUDENT", 21L, ORDER_ID);

        assertEquals(300L, response.getSubscriptionId());
        verify(applicationEventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void reconcile_RazorpayReportsCreated_Throws409AndStaysPending() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(pendingPayment()));
        when(razorpayClient.fetchOrder(ORDER_ID))
                .thenReturn(new OrderResponse(ORDER_ID, 19900L, "INR", "created", "r"));

        assertEquals(HttpStatus.CONFLICT, assertThrows(CustomException.class,
                () -> service().reconcileOrder("STUDENT", 21L, ORDER_ID)).getStatus());

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void reconcile_RazorpayDoesNotKnowOrder_Throws409() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(pendingPayment()));
        when(razorpayClient.fetchOrder(ORDER_ID)).thenReturn(null);

        assertEquals(HttpStatus.CONFLICT, assertThrows(CustomException.class,
                () -> service().reconcileOrder("STUDENT", 21L, ORDER_ID)).getStatus());
    }

    @Test
    void reconcile_AlreadySuccess_IsIdempotentAndNeverCallsRazorpay() {
        Payment done = pendingPayment();
        done.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(done));
        when(subscriptionRepository.findByPaymentId(77L)).thenReturn(Optional.of(Subscription.builder()
                .id(300L).userId(21L).plan(monthlyPlan()).paymentId(77L)
                .endDate(LocalDateTime.now().plusDays(30))
                .status(SubscriptionStatus.ACTIVE).build()));

        assertEquals(300L, service().reconcileOrder("STUDENT", 21L, ORDER_ID).getSubscriptionId());
        verify(razorpayClient, never()).fetchOrder(any());
    }

    @Test
    void reconcile_OtherUsersOrder_Throws404() {
        when(paymentRepository.findByRazorpayOrderId(ORDER_ID)).thenReturn(Optional.of(pendingPayment()));

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(CustomException.class,
                () -> service().reconcileOrder("STUDENT", 999L, ORDER_ID)).getStatus());
    }

    // ----- getMySubscription -----

    @Test
    void getMySubscription_NoRow_ReturnsVirtualFreePlan() {
        when(subscriptionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(21L, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of());
        when(planRepository.findByPlanName("FREE")).thenReturn(Optional.of(SubscriptionPlan.builder()
                .id(1L).planName("FREE").description("Free").price(BigDecimal.ZERO)
                .billingCycle("LIFETIME").build()));

        SubscriptionResponse response = service().getMySubscription("STUDENT", 21L);

        assertEquals("FREE", response.getPlanName());
        assertTrue(response.getActive());
        assertEquals(null, response.getId());
    }

    @Test
    void getMySubscription_MultipleActiveRows_ReturnsNewest() {
        // The finder returns a List precisely because nothing enforces one ACTIVE row per user.
        Subscription newest = Subscription.builder()
                .id(301L).userId(21L).plan(monthlyPlan()).paymentId(78L)
                .startDate(LocalDateTime.now()).endDate(LocalDateTime.now().plusDays(30))
                .status(SubscriptionStatus.ACTIVE).build();
        Subscription older = Subscription.builder()
                .id(300L).userId(21L).plan(monthlyPlan()).paymentId(77L)
                .startDate(LocalDateTime.now().minusDays(5)).endDate(LocalDateTime.now().plusDays(25))
                .status(SubscriptionStatus.ACTIVE).build();

        when(subscriptionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(21L, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of(newest, older));

        assertEquals(301L, service().getMySubscription("STUDENT", 21L).getId());
    }

    @Test
    void getMySubscription_ExpiredEndDate_FlipsStatusToExpiredAndReturnsFree() {
        Subscription expired = Subscription.builder()
                .id(300L).userId(21L).plan(monthlyPlan()).paymentId(77L)
                .startDate(LocalDateTime.now().minusDays(40))
                .endDate(LocalDateTime.now().minusDays(1))
                .status(SubscriptionStatus.ACTIVE).build();

        when(subscriptionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(21L, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of(expired));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(planRepository.findByPlanName("FREE")).thenReturn(Optional.empty());

        SubscriptionResponse response = service().getMySubscription("STUDENT", 21L);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertEquals(SubscriptionStatus.EXPIRED, captor.getValue().getStatus());
        assertEquals("FREE", response.getPlanName());
        assertTrue(response.getActive());
    }

    @Test
    void getMySubscription_ActiveRow_ReturnsItAsActive() {
        Subscription active = Subscription.builder()
                .id(300L).userId(21L).plan(monthlyPlan()).paymentId(77L)
                .startDate(LocalDateTime.now()).endDate(LocalDateTime.now().plusDays(30))
                .status(SubscriptionStatus.ACTIVE).build();
        when(subscriptionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(21L, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of(active));

        SubscriptionResponse response = service().getMySubscription("STUDENT", 21L);

        assertEquals("STUDENT_PREMIUM", response.getPlanName());
        assertTrue(response.getActive());
    }

    @Test
    void getMySubscription_RecruiterRole_Throws403() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(CustomException.class,
                () -> service().getMySubscription("RECRUITER", 21L)).getStatus());
    }

    // ----- admin -----

    @Test
    void getAllSubscriptions_Student_Throws403() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(CustomException.class,
                () -> service().getAllSubscriptions("STUDENT")).getStatus());
    }

    @Test
    void getAllSubscriptions_SuperAdmin_ReturnsAllWithComputedActiveFlag() {
        Subscription active = Subscription.builder()
                .id(300L).userId(21L).plan(monthlyPlan()).paymentId(77L)
                .startDate(LocalDateTime.now()).endDate(LocalDateTime.now().plusDays(30))
                .status(SubscriptionStatus.ACTIVE).build();
        Subscription cancelled = Subscription.builder()
                .id(299L).userId(22L).plan(monthlyPlan()).paymentId(76L)
                .startDate(LocalDateTime.now().minusDays(40)).endDate(LocalDateTime.now().plusDays(5))
                .status(SubscriptionStatus.CANCELLED).build();
        when(subscriptionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(active, cancelled));

        List<SubscriptionResponse> all = service().getAllSubscriptions("SUPER_ADMIN");

        assertEquals(2, all.size());
        assertTrue(all.get(0).getActive());
        assertFalse(all.get(1).getActive(), "a CANCELLED row must not report active even before endDate");
    }
}
