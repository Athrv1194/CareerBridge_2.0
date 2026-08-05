package com.careerbridge.payment.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GstTest {

    /**
     * The one invariant that actually matters on an invoice: the three parts must sum back to
     * exactly what was charged. Rounding each component independently would not guarantee this.
     */
    @ParameterizedTest
    @ValueSource(longs = {19900L, 499900L, 999900L, 100L, 1L, 0L})
    @DisplayName("split: taxable + cgst + sgst always sums to the exact total, for any input")
    void split_AnyAmount_ComponentsSumToTotal(long totalPaise) {
        Gst.GstSplit split = Gst.split(totalPaise);
        assertEquals(totalPaise, split.taxablePaise() + split.cgstPaise() + split.sgstPaise());
    }

    @Test
    @DisplayName("split: STUDENT_PREMIUM (Rs 199) splits into the exact worked values")
    void split_StudentPremium_MatchesWorkedValues() {
        Gst.GstSplit split = Gst.split(19900L);
        assertEquals(16864L, split.taxablePaise());
        assertEquals(1518L, split.cgstPaise());
        assertEquals(1518L, split.sgstPaise());
        assertEquals(19900L, split.totalPaise());
    }

    @Test
    @DisplayName("split: COLLEGE_PRO (Rs 9999) has an odd GST paisa, so CGST and SGST deliberately differ by one paisa")
    void split_CollegePro_HalvesDifferByOnePaisaOnPurpose() {
        Gst.GstSplit split = Gst.split(999900L);
        assertEquals(847373L, split.taxablePaise());
        assertEquals(76263L, split.cgstPaise());
        assertEquals(76264L, split.sgstPaise());
        // The asymmetry is correct, not a rounding bug: what must hold is the total, asserted above
        // by the parameterized test. Pinning it here so nobody "fixes" the halves toward equality
        // and breaks the sum invariant instead.
        assertEquals(1L, split.sgstPaise() - split.cgstPaise());
    }

    @Test
    @DisplayName("split: COLLEGE_BASIC (Rs 4999) splits evenly")
    void split_CollegeBasic_MatchesWorkedValues() {
        Gst.GstSplit split = Gst.split(499900L);
        assertEquals(423644L, split.taxablePaise());
        assertEquals(38128L, split.cgstPaise());
        assertEquals(38128L, split.sgstPaise());
    }

    @Test
    @DisplayName("split: zero total splits into all zeros without throwing")
    void split_Zero_AllZeros() {
        Gst.GstSplit split = Gst.split(0L);
        assertEquals(0L, split.taxablePaise());
        assertEquals(0L, split.cgstPaise());
        assertEquals(0L, split.sgstPaise());
        assertEquals(0L, split.totalPaise());
    }
}
