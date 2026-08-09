package com.careerbridge.payment.service;

import com.careerbridge.payment.constants.PaymentConstants;
import com.careerbridge.payment.dto.InvoiceData;
import com.careerbridge.payment.dto.InvoiceDownload;
import com.careerbridge.payment.exception.CustomException;
import com.careerbridge.payment.model.Payment;
import com.careerbridge.payment.model.PaymentStatus;
import com.careerbridge.payment.model.Subscription;
import com.careerbridge.payment.pdf.InvoicePdfBuilder;
import com.careerbridge.payment.repository.PaymentRepository;
import com.careerbridge.payment.repository.SubscriptionRepository;
import com.careerbridge.payment.util.Gst;
import com.careerbridge.payment.util.InvoiceNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Renders the invoice fresh on every call rather than storing it -- see the class comment on
 * InvoicePdfBuilder for why that is safe: everything it renders comes from immutable Payment
 * fields (amountPaise, currency, paidAt) plus the payment id itself, never from the mutable
 * SubscriptionPlan catalog row.
 */
@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoicePdfBuilder invoicePdfBuilder;

    public InvoiceServiceImpl(PaymentRepository paymentRepository,
                              SubscriptionRepository subscriptionRepository,
                              InvoicePdfBuilder invoicePdfBuilder) {
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.invoicePdfBuilder = invoicePdfBuilder;
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDownload getInvoice(String callerRole, Long callerId, Long paymentId) {
        // 404 rather than 403 throughout this method, matching resume-service's resume lookups and
        // PaymentServiceImpl.loadOwnedPayment: a caller has no legitimate reason to address another
        // user's invoice, and a 403 would confirm the payment id exists.
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException("Invoice not found", HttpStatus.NOT_FOUND));

        boolean isOwner = Objects.equals(payment.getUserId(), callerId);
        boolean isSuperAdmin = PaymentConstants.ROLE_SUPER_ADMIN.equals(callerRole);
        if (!isOwner && !isSuperAdmin) {
            throw new CustomException("Invoice not found", HttpStatus.NOT_FOUND);
        }

        // An invoice for money never taken must not exist -- there is nothing to invoice for a
        // PENDING or FAILED payment.
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new CustomException("No invoice exists for an unpaid order", HttpStatus.NOT_FOUND);
        }

        InvoiceData invoiceData = buildInvoiceData(payment);
        String fileName = invoiceData.getInvoiceNumber() + ".pdf";

        byte[] content;
        try {
            content = invoicePdfBuilder.build(invoiceData);
        } catch (IOException ex) {
            log.error("Invoice PDF generation failed for paymentId={}", paymentId, ex);
            throw new CustomException("Invoice generation failed - please try again",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return InvoiceDownload.builder()
                .fileName(fileName)
                .content(content)
                .build();
    }

    private InvoiceData buildInvoiceData(Payment payment) {
        // subscriptionRepository.findByPaymentId is legitimately Optional: payment_id carries a
        // real UNIQUE constraint. Absence is reachable (e.g. the subscription row was somehow lost
        // after a SUCCESS payment) and falls back to paidAt as both period bounds rather than
        // failing the whole invoice.
        Optional<Subscription> subscription = subscriptionRepository.findByPaymentId(payment.getId());

        LocalDate paidDate = payment.getPaidAt() != null
                ? payment.getPaidAt().toLocalDate()
                : LocalDate.now();

        LocalDate periodStart = subscription.map(s -> s.getStartDate().toLocalDate()).orElse(paidDate);
        LocalDate periodEnd = subscription.map(s -> s.getEndDate().toLocalDate()).orElse(paidDate);
        String billingCycle = subscription.map(s -> s.getPlan().getBillingCycle())
                .orElse(payment.getPlan().getBillingCycle());

        Gst.GstSplit gstSplit = Gst.split(payment.getAmountPaise());

        return InvoiceData.builder()
                .invoiceNumber(InvoiceNumber.forPayment(payment.getId()))
                .issueDate(paidDate)
                .userId(payment.getUserId())
                .planName(payment.getPlan().getPlanName())
                .billingCycle(billingCycle)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .razorpayOrderId(payment.getRazorpayOrderId())
                .razorpayPaymentId(payment.getRazorpayPaymentId())
                .currency(payment.getCurrency())
                .gstSplit(gstSplit)
                .build();
    }
}
