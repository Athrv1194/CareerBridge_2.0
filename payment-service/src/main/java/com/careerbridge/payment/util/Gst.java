package com.careerbridge.payment.util;

/**
 * Splits a GST-inclusive amount (integer paise) into taxable value + CGST (9%) + SGST (9%).
 *
 * The invoice total must equal what Razorpay actually charged, so the price is treated as
 * tax-inclusive, not exclusive -- an exclusive assumption would print a total that matches no
 * real transaction (e.g. a 234.82 total against a 199.00 charge).
 *
 * Both the GST amount and the SGST half are derived by SUBTRACTION from an already-rounded value,
 * never rounded independently. That is what guarantees taxable + cgst + sgst == totalPaise
 * exactly, even when the halves are not equal (an odd total GST paisa has to land on one side or
 * the other -- see GstTest for the COLLEGE_PRO case, where CGST and SGST differ by one paisa).
 * Rounding each component independently would not carry that guarantee.
 */
public final class Gst {

    private static final double GST_INCLUSIVE_DIVISOR = 118.0;
    private static final double TAXABLE_MULTIPLIER = 100.0;

    private Gst() {
    }

    public record GstSplit(long taxablePaise, long cgstPaise, long sgstPaise, long totalPaise) {
    }

    public static GstSplit split(long totalPaise) {
        long taxablePaise = Math.round(totalPaise * TAXABLE_MULTIPLIER / GST_INCLUSIVE_DIVISOR);
        long gstPaise = totalPaise - taxablePaise;
        long cgstPaise = gstPaise / 2;
        long sgstPaise = gstPaise - cgstPaise;
        return new GstSplit(taxablePaise, cgstPaise, sgstPaise, totalPaise);
    }
}
