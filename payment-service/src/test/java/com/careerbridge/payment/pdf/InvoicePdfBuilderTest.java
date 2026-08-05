package com.careerbridge.payment.pdf;

import com.careerbridge.payment.dto.InvoiceData;
import com.careerbridge.payment.util.Gst;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No Mockito here -- the builder has no collaborators. These run the real OpenPDF engine and
 * assert on the real bytes, matching resume-service's ResumePdfBuilderTest -- a mocked PDF test
 * would prove nothing about whether the library is actually wired correctly.
 */
class InvoicePdfBuilderTest {

    private final InvoicePdfBuilder builder = new InvoicePdfBuilder();

    /** Every PDF file begins with the literal bytes "%PDF". Cheapest possible validity check. */
    private static void assertIsPdf(byte[] bytes) {
        assertTrue(bytes.length > 4, "PDF is too short to contain a header");
        String header = new String(Arrays.copyOfRange(bytes, 0, 4), StandardCharsets.US_ASCII);
        assertTrue("%PDF".equals(header), "expected a %PDF header but got: " + header);
    }

    private static InvoiceData fullInvoice() {
        return InvoiceData.builder()
                .invoiceNumber("CB-INV-000042")
                .issueDate(LocalDate.of(2026, 8, 3))
                .userId(31L)
                .planName("STUDENT_PREMIUM")
                .billingCycle("MONTHLY")
                .periodStart(LocalDate.of(2026, 8, 3))
                .periodEnd(LocalDate.of(2026, 9, 2))
                .razorpayOrderId("order_TLFnEaYq0Xfrpo")
                .razorpayPaymentId("pay_TLFnEbZq0Xfrpp")
                .currency("INR")
                .gstSplit(Gst.split(19900L))
                .build();
    }

    @Test
    @DisplayName("build: a fully populated invoice renders a real, non-trivial PDF")
    void build_FullInvoice_ProducesValidPdf() throws Exception {
        byte[] pdf = builder.build(fullInvoice());

        assertIsPdf(pdf);
        // A header-only PDF is roughly 500 bytes; a two-table document clears 1500 easily.
        assertTrue(pdf.length > 1500, "expected a substantial PDF but got " + pdf.length + " bytes");
    }

    /**
     * reconcileOrder's abandoned-checkout path can complete a subscription with no
     * razorpayPaymentId ever set on some earlier code path, and a missing subscription row falls
     * back to paidAt for both period bounds -- both must render, not throw.
     */
    @Test
    @DisplayName("build: missing payment id and missing period bounds do not throw")
    void build_SparseInvoice_DoesNotThrow() {
        InvoiceData sparse = InvoiceData.builder()
                .invoiceNumber("CB-INV-000001")
                .issueDate(LocalDate.of(2026, 1, 1))
                .userId(1L)
                .planName("COLLEGE_PRO")
                .billingCycle("SEMESTER")
                .periodStart(LocalDate.of(2026, 1, 1))
                .periodEnd(LocalDate.of(2026, 1, 1))
                .razorpayOrderId("order_x")
                .razorpayPaymentId(null)
                .currency("INR")
                .gstSplit(Gst.split(999900L))
                .build();

        byte[] pdf = assertDoesNotThrow(() -> builder.build(sparse));
        assertIsPdf(pdf);
    }

    @Test
    @DisplayName("build: a zero-amount split still renders without throwing")
    void build_ZeroAmount_DoesNotThrow() {
        InvoiceData zero = InvoiceData.builder()
                .invoiceNumber("CB-INV-000002")
                .issueDate(LocalDate.now())
                .userId(2L)
                .planName("FREE")
                .billingCycle("LIFETIME")
                .periodStart(LocalDate.now())
                .periodEnd(LocalDate.now())
                .razorpayOrderId("order_free")
                .razorpayPaymentId("pay_free")
                .currency("INR")
                .gstSplit(Gst.split(0L))
                .build();

        byte[] pdf = assertDoesNotThrow(() -> builder.build(zero));
        assertIsPdf(pdf);
    }
}
