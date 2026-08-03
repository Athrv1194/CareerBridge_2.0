package com.careerbridge.payment.service;

import com.careerbridge.payment.dto.CreateOrderRequest;
import com.careerbridge.payment.dto.CreateOrderResponse;
import com.careerbridge.payment.dto.PlanResponse;
import com.careerbridge.payment.dto.external.RazorpayOrderDtos.OrderResponse;
import com.careerbridge.payment.exception.CustomException;
import com.careerbridge.payment.model.Payment;
import com.careerbridge.payment.model.PaymentStatus;
import com.careerbridge.payment.model.SubscriptionPlan;
import com.careerbridge.payment.repository.PaymentRepository;
import com.careerbridge.payment.repository.SubscriptionPlanRepository;
import com.careerbridge.payment.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceOrderTest {

    private static final String SECRET = "testsecret";

    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private RazorpayClient razorpayClient;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    private PaymentServiceImpl service() {
        return new PaymentServiceImpl(planRepository, paymentRepository, subscriptionRepository,
                razorpayClient, applicationEventPublisher, SECRET);
    }

    private static SubscriptionPlan premiumPlan() {
        return SubscriptionPlan.builder()
                .id(2L).planName("STUDENT_PREMIUM").description("Premium")
                .price(new BigDecimal("199.00")).currency("INR").billingCycle("MONTHLY")
                .features("[\"AI Career Coach\",\"Resume Builder\"]").isActive(true)
                .build();
    }

    private static SubscriptionPlan freePlan() {
        return SubscriptionPlan.builder()
                .id(1L).planName("FREE").description("Free")
                .price(BigDecimal.ZERO).currency("INR").billingCycle("LIFETIME")
                .features("[\"Assessment\"]").isActive(true)
                .build();
    }

    // ----- createOrder -----

    @Test
    void createOrder_ValidPlan_PersistsPendingPaymentWithRazorpayOrderId() {
        when(planRepository.findById(2L)).thenReturn(Optional.of(premiumPlan()));
        when(razorpayClient.createOrder(anyLong(), anyString(), anyString()))
                .thenReturn(new OrderResponse("order_abc", 19900L, "INR", "created", "CB-21-1"));
        when(razorpayClient.getKeyId()).thenReturn("rzp_test_abc");
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(77L);
            return p;
        });

        CreateOrderResponse response = service().createOrder("STUDENT", 21L,
                CreateOrderRequest.builder().planId(2L).build());

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();
        assertEquals(PaymentStatus.PENDING, saved.getStatus());
        assertEquals("order_abc", saved.getRazorpayOrderId());
        assertEquals(19900L, saved.getAmountPaise());
        assertEquals(21L, saved.getUserId());

        assertEquals("order_abc", response.getRazorpayOrderId());
        assertEquals(19900L, response.getAmountPaise());
        assertEquals("rzp_test_abc", response.getKeyId());
    }

    @Test
    void createOrder_SendsAmountInPaiseNotRupees() {
        when(planRepository.findById(2L)).thenReturn(Optional.of(premiumPlan()));
        when(razorpayClient.createOrder(anyLong(), anyString(), anyString()))
                .thenReturn(new OrderResponse("order_abc", 19900L, "INR", "created", "r"));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().createOrder("STUDENT", 21L, CreateOrderRequest.builder().planId(2L).build());

        // 19900 paise, not 199 rupees -- the classic factor-of-100 payment bug.
        verify(razorpayClient).createOrder(org.mockito.ArgumentMatchers.eq(19900L),
                org.mockito.ArgumentMatchers.eq("INR"), anyString());
    }

    @Test
    void createOrder_FreePlan_Throws400() {
        when(planRepository.findById(1L)).thenReturn(Optional.of(freePlan()));

        CustomException ex = assertThrows(CustomException.class,
                () -> service().createOrder("STUDENT", 21L, CreateOrderRequest.builder().planId(1L).build()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("free plan requires no purchase"));
    }

    @Test
    void createOrder_FreePlan_NeverCallsRazorpayAndWritesNoPaymentRow() {
        when(planRepository.findById(1L)).thenReturn(Optional.of(freePlan()));

        assertThrows(CustomException.class,
                () -> service().createOrder("STUDENT", 21L, CreateOrderRequest.builder().planId(1L).build()));

        verify(razorpayClient, never()).createOrder(anyLong(), anyString(), anyString());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createOrder_UnknownPlan_Throws404() {
        when(planRepository.findById(99L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(CustomException.class,
                () -> service().createOrder("STUDENT", 21L,
                        CreateOrderRequest.builder().planId(99L).build())).getStatus());
    }

    @Test
    void createOrder_InactivePlan_Throws400() {
        SubscriptionPlan retired = premiumPlan();
        retired.setIsActive(false);
        when(planRepository.findById(2L)).thenReturn(Optional.of(retired));

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(CustomException.class,
                () -> service().createOrder("STUDENT", 21L,
                        CreateOrderRequest.builder().planId(2L).build())).getStatus());
    }

    @Test
    void createOrder_RecruiterRole_Throws403() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(CustomException.class,
                () -> service().createOrder("RECRUITER", 21L,
                        CreateOrderRequest.builder().planId(2L).build())).getStatus());
    }

    @Test
    void createOrder_OrgAdminRole_IsAllowed() {
        when(planRepository.findById(3L)).thenReturn(Optional.of(SubscriptionPlan.builder()
                .id(3L).planName("COLLEGE_BASIC").price(new BigDecimal("4999.00"))
                .currency("INR").billingCycle("SEMESTER").isActive(true).build()));
        when(razorpayClient.createOrder(anyLong(), anyString(), anyString()))
                .thenReturn(new OrderResponse("order_col", 499900L, "INR", "created", "r"));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("order_col", service().createOrder("ORG_ADMIN", 5L,
                CreateOrderRequest.builder().planId(3L).build()).getRazorpayOrderId());
    }

    @Test
    void createOrder_RazorpayUnavailable_Throws503AndWritesNoPaymentRow() {
        when(planRepository.findById(2L)).thenReturn(Optional.of(premiumPlan()));
        when(razorpayClient.createOrder(anyLong(), anyString(), anyString()))
                .thenThrow(new CustomException("down", HttpStatus.SERVICE_UNAVAILABLE));

        CustomException ex = assertThrows(CustomException.class,
                () -> service().createOrder("STUDENT", 21L, CreateOrderRequest.builder().planId(2L).build()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        // Razorpay is called FIRST and the row persisted SECOND, so a failed call leaves nothing
        // behind -- no orphan PENDING row with a null razorpay_order_id.
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createOrder_ReceiptIsWithinRazorpayLimit() {
        when(planRepository.findById(2L)).thenReturn(Optional.of(premiumPlan()));
        when(razorpayClient.createOrder(anyLong(), anyString(), anyString()))
                .thenReturn(new OrderResponse("order_abc", 19900L, "INR", "created", "r"));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().createOrder("STUDENT", 21L, CreateOrderRequest.builder().planId(2L).build());

        ArgumentCaptor<String> receipt = ArgumentCaptor.forClass(String.class);
        verify(razorpayClient).createOrder(anyLong(), anyString(), receipt.capture());
        assertTrue(receipt.getValue().length() <= 40, "Razorpay caps receipt at 40 chars");
    }

    // ----- getActivePlans -----

    @Test
    void getActivePlans_ParsesFeaturesJsonIntoRealArray() {
        when(planRepository.findByIsActiveTrueOrderByPriceAsc()).thenReturn(List.of(premiumPlan()));

        List<PlanResponse> plans = service().getActivePlans();

        assertEquals(1, plans.size());
        assertEquals(List.of("AI Career Coach", "Resume Builder"), plans.get(0).getFeatures());
    }

    @Test
    void getActivePlans_UsesActiveOrderedByPriceFinder() {
        when(planRepository.findByIsActiveTrueOrderByPriceAsc())
                .thenReturn(List.of(freePlan(), premiumPlan()));

        List<PlanResponse> plans = service().getActivePlans();

        assertEquals(List.of("FREE", "STUDENT_PREMIUM"),
                plans.stream().map(PlanResponse::getPlanName).toList());
        verify(planRepository).findByIsActiveTrueOrderByPriceAsc();
        verify(planRepository, never()).findAll();
    }

    @Test
    void parseFeatures_MalformedJson_ReturnsEmptyListNotException() {
        // A bad catalog value must not 500 the public pricing page.
        assertEquals(List.of(), PaymentServiceImpl.parseFeatures("not json"));
        assertEquals(List.of(), PaymentServiceImpl.parseFeatures(null));
        assertEquals(List.of(), PaymentServiceImpl.parseFeatures("[]"));
    }
}
