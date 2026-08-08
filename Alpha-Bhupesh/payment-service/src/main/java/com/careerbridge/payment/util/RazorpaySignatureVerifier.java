package com.careerbridge.payment.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Razorpay payment-signature verification, hand-rolled rather than via com.razorpay.Utils.
 *
 * The formula, verbatim from Razorpay's docs:
 *     generated_signature = hmac_sha256(order_id + "|" + razorpay_payment_id, secret)
 * where secret is the key SECRET (never the key id), and the result is compared against the
 * razorpay_signature the browser returned. Razorpay emits lowercase hex; HexFormat.of() is
 * lowercase by default.
 *
 * Two reasons this is not the SDK. First, the SDK would pull okhttp 3.10.0, org.json, commons-codec
 * and commons-text into a Boot 4 application for what is fifteen lines of JDK. Second, and more
 * importantly, the comparison below uses MessageDigest.isEqual, which is constant-time; a plain
 * String.equals short-circuits on the first differing character and leaks, in principle, how much
 * of a forged signature was correct.
 *
 * Everything here is static and stateless. The secret is passed in per call rather than held as a
 * field, so nothing in this class can ever end up in a log line or a stack frame that outlives the
 * call.
 */
public final class RazorpaySignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private RazorpaySignatureVerifier() {
    }

    /**
     * Never throws for bad input -- a malformed or absent signature is a rejected payment, not a
     * server fault. Returns false for null/blank arguments rather than NPEing on a request body
     * that passed @NotBlank but arrived some other way (reconcile, a future webhook).
     */
    public static boolean matches(String razorpayOrderId,
                                  String razorpayPaymentId,
                                  String razorpaySignature,
                                  String keySecret) {
        if (isBlank(razorpayOrderId) || isBlank(razorpayPaymentId)
                || isBlank(razorpaySignature) || isBlank(keySecret)) {
            return false;
        }

        String expected = hmacSha256Hex(razorpayOrderId + "|" + razorpayPaymentId, keySecret);

        // Constant-time. Both sides are compared as raw bytes of the hex text, which is fine --
        // equal hex strings mean equal digests, and the timing property is what matters here.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                razorpaySignature.trim().getBytes(StandardCharsets.UTF_8));
    }

    /** Lowercase hex HMAC-SHA256, exposed for the tests to pin the digest itself. */
    static String hmacSha256Hex(String payload, String keySecret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            // HmacSHA256 is mandated by the JDK spec, so this is unreachable on any real JVM.
            // The message deliberately carries no payload and no key material.
            throw new IllegalStateException("HMAC-SHA256 is unavailable on this JVM", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
