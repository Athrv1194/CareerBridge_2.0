package com.careerbridge.payment.util;

/**
 * "CB-INV-" plus the Payment id, zero-padded to 6 digits. Payment.id is the primary key, so this
 * is unique for free and needs no sequence, no new column, and no persistence at all -- it is
 * derived at render time from an id that already exists.
 */
public final class InvoiceNumber {

    private InvoiceNumber() {
    }

    public static String forPayment(Long paymentId) {
        return "CB-INV-" + String.format("%06d", paymentId);
    }
}
