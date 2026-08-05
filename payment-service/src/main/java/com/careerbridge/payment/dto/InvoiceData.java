package com.careerbridge.payment.dto;

import com.careerbridge.payment.util.Gst;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Plain render input for InvoicePdfBuilder -- no JPA entity crosses into the pdf package, mirroring
 * how ResumePdfBuilder takes a StudentProfileDto rather than the entity itself.
 *
 * Amounts here are display BigDecimal rupees (derived from the GstSplit's integer paise), never
 * the raw paise -- the builder only ever formats, it does not do tax arithmetic itself.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceData {

    private String invoiceNumber;
    private LocalDate issueDate;

    private Long userId;
    private String planName;
    private String billingCycle;

    private LocalDate periodStart;
    private LocalDate periodEnd;

    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String currency;

    private Gst.GstSplit gstSplit;
}
