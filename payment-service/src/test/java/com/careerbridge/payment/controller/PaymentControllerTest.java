package com.careerbridge.payment.controller;

import com.careerbridge.payment.dto.CreateOrderResponse;
import com.careerbridge.payment.dto.PaymentVerifyResponse;
import com.careerbridge.payment.dto.PlanResponse;
import com.careerbridge.payment.dto.SubscriptionResponse;
import com.careerbridge.payment.exception.CustomException;
import com.careerbridge.payment.exception.GlobalExceptionHandler;
import com.careerbridge.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc rather than @WebMvcTest: no Spring context, so these run without a database or
 * a broker. GlobalExceptionHandler is registered explicitly (standalone setup does not pick up
 * @RestControllerAdvice by scanning) and a real LocalValidatorFactoryBean is wired in, so @Valid
 * genuinely fires instead of being silently skipped.
 */
@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new PaymentController(paymentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void getPlans_NoAuthHeadersAtAll_Returns200() throws Exception {
        // The pricing page is public. The gateway forwards a public path with NO identity headers,
        // so this endpoint must not require any -- a required header here would 400 every call.
        when(paymentService.getActivePlans()).thenReturn(List.of(PlanResponse.builder()
                .id(1L).planName("FREE").price(BigDecimal.ZERO).features(List.of("Assessment")).build()));

        mockMvc().perform(get("/api/payment/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].planName").value("FREE"))
                .andExpect(jsonPath("$[0].features[0]").value("Assessment"));
    }

    @Test
    void createOrder_Valid_Returns201() throws Exception {
        when(paymentService.createOrder(anyString(), anyLong(), any())).thenReturn(
                CreateOrderResponse.builder().paymentId(77L).razorpayOrderId("order_abc")
                        .amountPaise(19900L).keyId("rzp_test_abc").build());

        mockMvc().perform(post("/api/payment/orders")
                        .header(USER_ID_HEADER, "21").header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.razorpayOrderId").value("order_abc"))
                .andExpect(jsonPath("$.amountPaise").value(19900));
    }

    @Test
    void createOrder_MissingPlanId_Returns400() throws Exception {
        mockMvc().perform(post("/api/payment/orders")
                        .header(USER_ID_HEADER, "21").header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.planId").value("Plan ID is required"));
    }

    @Test
    void createOrder_MissingUserIdHeader_Returns400() throws Exception {
        mockMvc().perform(post("/api/payment/orders")
                        .header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_NonNumericUserIdHeader_Returns400() throws Exception {
        // Pins the MethodArgumentTypeMismatchException handler -- without it this is a 500.
        mockMvc().perform(post("/api/payment/orders")
                        .header(USER_ID_HEADER, "abc").header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_MalformedJson_Returns400() throws Exception {
        // Pins the HttpMessageNotReadableException handler -- without it this is a 500.
        mockMvc().perform(post("/api/payment/orders")
                        .header(USER_ID_HEADER, "21").header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createOrder_ServiceThrows403_Returns403() throws Exception {
        when(paymentService.createOrder(anyString(), anyLong(), any()))
                .thenThrow(new CustomException("Only STUDENT or ORG_ADMIN may manage a subscription",
                        HttpStatus.FORBIDDEN));

        mockMvc().perform(post("/api/payment/orders")
                        .header(USER_ID_HEADER, "21").header(USER_ROLE_HEADER, "RECRUITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":2}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void verify_Valid_Returns200() throws Exception {
        when(paymentService.verifyPayment(anyString(), anyLong(), any())).thenReturn(
                PaymentVerifyResponse.builder().subscriptionId(300L).planName("STUDENT_PREMIUM").build());

        mockMvc().perform(post("/api/payment/verify")
                        .header(USER_ID_HEADER, "21").header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"razorpayOrderId":"order_abc","razorpayPaymentId":"pay_abc",
                                 "razorpaySignature":"sig"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionId").value(300));
    }

    @Test
    void verify_MissingSignature_Returns400() throws Exception {
        mockMvc().perform(post("/api/payment/verify")
                        .header(USER_ID_HEADER, "21").header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"razorpayOrderId\":\"order_abc\",\"razorpayPaymentId\":\"pay_abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.razorpaySignature").exists());
    }

    @Test
    void verify_BlankOrderId_Returns400() throws Exception {
        mockMvc().perform(post("/api/payment/verify")
                        .header(USER_ID_HEADER, "21").header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"razorpayOrderId":"  ","razorpayPaymentId":"pay_abc",
                                 "razorpaySignature":"sig"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verify_InvalidSignature_Returns400() throws Exception {
        when(paymentService.verifyPayment(anyString(), anyLong(), any()))
                .thenThrow(new CustomException("Payment verification failed - invalid signature",
                        HttpStatus.BAD_REQUEST));

        mockMvc().perform(post("/api/payment/verify")
                        .header(USER_ID_HEADER, "21").header(USER_ROLE_HEADER, "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"razorpayOrderId":"order_abc","razorpayPaymentId":"pay_abc",
                                 "razorpaySignature":"forged"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reconcile_Valid_Returns200() throws Exception {
        when(paymentService.reconcileOrder(anyString(), anyLong(), anyString())).thenReturn(
                PaymentVerifyResponse.builder().subscriptionId(300L).build());

        mockMvc().perform(post("/api/payment/orders/order_abc/reconcile")
                        .header(USER_ID_HEADER, "21").header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isOk());
    }

    @Test
    void reconcile_NotPaid_Returns409() throws Exception {
        when(paymentService.reconcileOrder(anyString(), anyLong(), anyString()))
                .thenThrow(new CustomException("Razorpay has not recorded this order as paid",
                        HttpStatus.CONFLICT));

        mockMvc().perform(post("/api/payment/orders/order_abc/reconcile")
                        .header(USER_ID_HEADER, "21").header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isConflict());
    }

    @Test
    void getMySubscription_Valid_Returns200() throws Exception {
        when(paymentService.getMySubscription("STUDENT", 21L)).thenReturn(
                SubscriptionResponse.builder().planName("FREE").active(true).build());

        mockMvc().perform(get("/api/payment/subscription/my")
                        .header(USER_ID_HEADER, "21").header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planName").value("FREE"))
                // Boxed Boolean, so the JSON key stays "active" rather than collapsing.
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getMyPayments_Valid_Returns200() throws Exception {
        when(paymentService.getMyPayments("STUDENT", 21L)).thenReturn(List.of());

        mockMvc().perform(get("/api/payment/payments/my")
                        .header(USER_ID_HEADER, "21").header(USER_ROLE_HEADER, "STUDENT"))
                .andExpect(status().isOk());
    }

    @Test
    void getAllSubscriptions_SuperAdmin_Returns200() throws Exception {
        when(paymentService.getAllSubscriptions("SUPER_ADMIN")).thenReturn(List.of());

        mockMvc().perform(get("/api/payment/admin/subscriptions")
                        .header(USER_ROLE_HEADER, "SUPER_ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void getAllSubscriptions_MissingRoleHeader_Returns400() throws Exception {
        mockMvc().perform(get("/api/payment/admin/subscriptions"))
                .andExpect(status().isBadRequest());
    }
}
