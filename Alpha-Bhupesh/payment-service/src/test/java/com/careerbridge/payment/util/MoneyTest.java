package com.careerbridge.payment.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @Test
    void toPaise_199Rupees_Returns19900() {
        assertEquals(19900L, Money.toPaise(new BigDecimal("199.00")));
    }

    @Test
    void toPaise_ScaleZeroInput_ReturnsSameAsScaleTwo() {
        // The catalog stores numeric(38,2), but a hand-built BigDecimal("199") has scale 0.
        // movePointRight shifts the scale, so both forms must agree.
        assertEquals(19900L, Money.toPaise(new BigDecimal("199")));
    }

    @Test
    void toPaise_DocumentedRazorpayExample_Returns22225() {
        // Razorpay's own docs: "for an actual amount of Rs 222.25, the value should be 22225".
        assertEquals(22225L, Money.toPaise(new BigDecimal("222.25")));
    }

    @Test
    void toPaise_LargestPlanPrice_Returns999900() {
        assertEquals(999900L, Money.toPaise(new BigDecimal("9999.00")));
    }

    @Test
    void toPaise_Zero_ReturnsZero() {
        assertEquals(0L, Money.toPaise(BigDecimal.ZERO));
    }

    @Test
    void toPaise_SubPaisePrecision_ThrowsArithmeticException() {
        // A price of 199.005 rupees is half a paisa, which cannot be charged. Loud failure beats
        // silently rounding someone's money in either direction.
        assertThrows(ArithmeticException.class, () -> Money.toPaise(new BigDecimal("199.005")));
    }

    @Test
    void toPaise_Null_ThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> Money.toPaise(null));
    }

    @Test
    void toRupees_RoundTripsToPaise() {
        assertEquals(0, new BigDecimal("199.00").compareTo(Money.toRupees(19900L)));
    }
}
