package com.careerbridge.payment.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The expected digests below are LITERALS, computed independently with openssl:
 *
 *   printf 'order_test123|pay_test456' | openssl dgst -sha256 -hmac 'testsecret' -hex
 *
 * They are deliberately not recomputed by calling hmacSha256Hex, because a test that derives its
 * expectation from the code under test passes for the wrong reason -- it would still be green if
 * the concatenation order, the separator, the charset or the hex case were all wrong together.
 */
class RazorpaySignatureVerifierTest {

    private static final String ORDER_ID = "order_test123";
    private static final String PAYMENT_ID = "pay_test456";
    private static final String SECRET = "testsecret";

    /** openssl: HMAC-SHA256("order_test123|pay_test456", "testsecret") */
    private static final String VALID_SIGNATURE =
            "a14b537deda7f4f70d98f120b68fecda8d9b8530f5f5f2a4e65b66e391add2cc";

    /** openssl: HMAC-SHA256("pay_test456|order_test123", "testsecret") -- operands swapped */
    private static final String SWAPPED_OPERANDS_SIGNATURE =
            "04e1e5eb27cb40537ce8ba8d961a37de9d53acc68637105074af39a377c37280";

    /** openssl: HMAC-SHA256("order_test123|pay_test456", "wrongsecret") */
    private static final String WRONG_SECRET_SIGNATURE =
            "6880b68679bfe2bd612c31709309dbee30426d84840c829e3a182f527fb03f87";

    @Test
    void hmacSha256Hex_KnownVector_MatchesOpensslLowercaseHex() {
        assertEquals(VALID_SIGNATURE,
                RazorpaySignatureVerifier.hmacSha256Hex(ORDER_ID + "|" + PAYMENT_ID, SECRET));
    }

    @Test
    void matches_KnownVector_ReturnsTrue() {
        assertTrue(RazorpaySignatureVerifier.matches(ORDER_ID, PAYMENT_ID, VALID_SIGNATURE, SECRET));
    }

    @Test
    void matches_ConcatenationIsOrderIdPipePaymentId_NotTheReverse() {
        // Razorpay signs order_id first. If the implementation ever swaps them, the valid signature
        // stops matching and the swapped one starts -- both halves are asserted so the test cannot
        // pass with the operands reversed.
        assertTrue(RazorpaySignatureVerifier.matches(ORDER_ID, PAYMENT_ID, VALID_SIGNATURE, SECRET));
        assertFalse(RazorpaySignatureVerifier.matches(ORDER_ID, PAYMENT_ID,
                SWAPPED_OPERANDS_SIGNATURE, SECRET));
    }

    @Test
    void matches_WrongSecret_ReturnsFalse() {
        assertFalse(RazorpaySignatureVerifier.matches(ORDER_ID, PAYMENT_ID,
                WRONG_SECRET_SIGNATURE, SECRET));
    }

    @Test
    void matches_WrongPaymentId_ReturnsFalse() {
        assertFalse(RazorpaySignatureVerifier.matches(ORDER_ID, "pay_tampered", VALID_SIGNATURE, SECRET));
    }

    @Test
    void matches_WrongOrderId_ReturnsFalse() {
        assertFalse(RazorpaySignatureVerifier.matches("order_tampered", PAYMENT_ID, VALID_SIGNATURE, SECRET));
    }

    @Test
    void matches_TruncatedSignature_ReturnsFalse() {
        // MessageDigest.isEqual is length-aware, so a prefix of a valid signature must not pass.
        assertFalse(RazorpaySignatureVerifier.matches(ORDER_ID, PAYMENT_ID,
                VALID_SIGNATURE.substring(0, 32), SECRET));
    }

    @Test
    void matches_SignatureWithExtraTrailingCharacter_ReturnsFalse() {
        assertFalse(RazorpaySignatureVerifier.matches(ORDER_ID, PAYMENT_ID,
                VALID_SIGNATURE + "0", SECRET));
    }

    @Test
    void matches_UppercaseHexSignature_ReturnsFalse() {
        // Razorpay emits lowercase. Pinning the behaviour rather than silently normalising: if a
        // future Razorpay change starts sending uppercase, this test fails loudly and points at
        // the decision, instead of the verifier quietly accepting both forms.
        assertFalse(RazorpaySignatureVerifier.matches(ORDER_ID, PAYMENT_ID,
                VALID_SIGNATURE.toUpperCase(), SECRET));
    }

    @Test
    void matches_NullSignature_ReturnsFalseNotNpe() {
        assertFalse(RazorpaySignatureVerifier.matches(ORDER_ID, PAYMENT_ID, null, SECRET));
    }

    @Test
    void matches_BlankSignature_ReturnsFalse() {
        assertFalse(RazorpaySignatureVerifier.matches(ORDER_ID, PAYMENT_ID, "   ", SECRET));
    }

    @Test
    void matches_BlankSecret_ReturnsFalse() {
        // An unconfigured RAZORPAY_KEY_SECRET must never make verification succeed.
        assertFalse(RazorpaySignatureVerifier.matches(ORDER_ID, PAYMENT_ID, VALID_SIGNATURE, ""));
    }

    @Test
    void matches_NullOrderOrPaymentId_ReturnsFalseNotNpe() {
        assertFalse(RazorpaySignatureVerifier.matches(null, PAYMENT_ID, VALID_SIGNATURE, SECRET));
        assertFalse(RazorpaySignatureVerifier.matches(ORDER_ID, null, VALID_SIGNATURE, SECRET));
    }
}
