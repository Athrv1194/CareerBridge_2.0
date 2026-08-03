package com.careerbridge.payment.util;

import java.math.BigDecimal;

/**
 * Rupees to paise, and back for display.
 *
 * Razorpay takes the amount as an integer in the currency subunit: "for an actual amount of
 * Rs 222.25, the value of this field should be 22225". Getting this wrong by a factor of 100 in
 * either direction is the classic payment-integration bug, so the conversion lives in one place
 * with its own tests rather than being inlined at the call site.
 */
public final class Money {

    /** Razorpay rejects anything below one rupee. */
    public static final long MINIMUM_CHARGEABLE_PAISE = 100L;

    private static final BigDecimal PAISE_PER_RUPEE = BigDecimal.valueOf(100);

    private Money() {
    }

    /**
     * movePointRight(2) shifts the scale rather than multiplying and rounding, so it cannot
     * silently truncate a fractional paisa. longValueExact then throws ArithmeticException on
     * either a non-zero fraction or an overflow -- both are loud failures, which is what a money
     * path wants. int would be enough for any realistic plan price (Integer.MAX_VALUE is about
     * Rs 2.1 crore) but long costs nothing and cannot be wrong.
     */
    public static long toPaise(BigDecimal rupees) {
        if (rupees == null) {
            throw new IllegalArgumentException("Amount must not be null");
        }
        return rupees.movePointRight(2).longValueExact();
    }

    /** For response DTOs only. Never used to compute what is sent to Razorpay. */
    public static BigDecimal toRupees(long paise) {
        return BigDecimal.valueOf(paise).divide(PAISE_PER_RUPEE);
    }
}
