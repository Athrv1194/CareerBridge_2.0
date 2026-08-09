package com.careerbridge.payment.service;

import com.careerbridge.payment.constants.PaymentConstants;
import com.careerbridge.payment.dto.InvoiceDownload;
import com.careerbridge.payment.exception.CustomException;
import com.careerbridge.payment.model.Payment;
import com.careerbridge.payment.model.PaymentStatus;
import com.careerbridge.payment.model.Subscription;
import com.careerbridge.payment.model.SubscriptionPlan;
import com.careerbridge.payment.pdf.InvoicePdfBuilder;
import com.careerbridge.payment.repository.PaymentRepository;
import com.careerbridge.payment.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private SubscriptionRepository subscriptionRepository;

    // The real builder, not a mock: the point of this test class is RBAC and status guards, and
    // asserting on real PDF bytes (via InvoicePdfBuilderTest's own suite) is cheaper and more
    // honest than mocking a byte[] return value here.
    private final InvoicePdfBuilder invoicePdfBuilder = new InvoicePdfBuilder();

    private InvoiceServiceImpl service() {
        return new InvoiceServiceImpl(paymentRepository, subscriptionRepository, invoicePdfBuilder);
    }

    private static SubscriptionPlan plan() {
        return SubscriptionPlan.builder()
                .id(2L).planName("STUDENT_PREMIUM").description("Premium")
                .price(new BigDecimal("199.00")).currency("INR").billingCycle("MONTHLY")
                .isActive(true).build();
    }

    private static Payment successPayment(Long paymentId, Long userId) {
        return Payment.builder()
                .id(paymentId).userId(userId).plan(plan())
                .razorpayOrderId("order_x").razorpayPaymentId("pay_x")
                .amountPaise(19900L).currency("INR")
                .status(PaymentStatus.SUCCESS)
                .paidAt(LocalDateTime.of(2026, 8, 3, 10, 0))
                .build();
    }

    private static Subscription subscription(Long paymentId, Long userId) {
        return Subscription.builder()
                .id(9L).userId(userId).plan(plan()).paymentId(paymentId)
                .startDate(LocalDateTime.of(2026, 8, 3, 10, 0))
                .endDate(LocalDateTime.of(2026, 9, 2, 10, 0))
                .build();
    }

    @Test
    void getInvoice_Owner_ReturnsPdf() {
        Payment payment = successPayment(42L, 31L);
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));
        when(subscriptionRepository.findByPaymentId(42L)).thenReturn(Optional.of(subscription(42L, 31L)));

        InvoiceDownload download = service().getInvoice(PaymentConstants.ROLE_STUDENT, 31L, 42L);

        assertEquals("CB-INV-000042.pdf", download.getFileName());
        assertTrue(download.getContent().length > 100, "expected real PDF bytes");
    }

    @Test
    void getInvoice_SuperAdmin_ReturnsPdfForAnyUser() {
        Payment payment = successPayment(42L, 31L);
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));
        when(subscriptionRepository.findByPaymentId(42L)).thenReturn(Optional.of(subscription(42L, 31L)));

        InvoiceDownload download =
                service().getInvoice(PaymentConstants.ROLE_SUPER_ADMIN, 999L, 42L);

        assertEquals("CB-INV-000042.pdf", download.getFileName());
    }

    /**
     * 404, never 403: a caller who is neither the owner nor SUPER_ADMIN has no legitimate reason to
     * address this payment id, matching resume-service's resume lookups and
     * PaymentServiceImpl.loadOwnedPayment -- a 403 would confirm the id exists.
     */
    @Test
    void getInvoice_NotOwnerNotAdmin_ThrowsNotFound() {
        Payment payment = successPayment(42L, 31L);
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));

        CustomException ex = assertThrows(CustomException.class,
                () -> service().getInvoice(PaymentConstants.ROLE_STUDENT, 999L, 42L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void getInvoice_PaymentNotFound_ThrowsNotFound() {
        when(paymentRepository.findById(42L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> service().getInvoice(PaymentConstants.ROLE_STUDENT, 31L, 42L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    /** An invoice for money never taken must not exist. */
    @Test
    void getInvoice_PendingPayment_ThrowsNotFound() {
        Payment pending = successPayment(42L, 31L);
        pending.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(pending));

        CustomException ex = assertThrows(CustomException.class,
                () -> service().getInvoice(PaymentConstants.ROLE_STUDENT, 31L, 42L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void getInvoice_FailedPayment_ThrowsNotFound() {
        Payment failed = successPayment(42L, 31L);
        failed.setStatus(PaymentStatus.FAILED);
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(failed));

        CustomException ex = assertThrows(CustomException.class,
                () -> service().getInvoice(PaymentConstants.ROLE_STUDENT, 31L, 42L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    /**
     * A SUCCESS payment can legitimately have no subscription row (the same self-healing gap
     * PaymentServiceImpl.completedResponseOrNull documents) -- the invoice must still render,
     * falling back to paidAt for both period bounds rather than failing the whole request.
     */
    @Test
    void getInvoice_NoSubscriptionRow_FallsBackToPaidAt() {
        Payment payment = successPayment(42L, 31L);
        when(paymentRepository.findById(42L)).thenReturn(Optional.of(payment));
        when(subscriptionRepository.findByPaymentId(42L)).thenReturn(Optional.empty());

        InvoiceDownload download = service().getInvoice(PaymentConstants.ROLE_STUDENT, 31L, 42L);

        assertEquals("CB-INV-000042.pdf", download.getFileName());
        assertTrue(download.getContent().length > 100, "expected real PDF bytes");
    }
}
