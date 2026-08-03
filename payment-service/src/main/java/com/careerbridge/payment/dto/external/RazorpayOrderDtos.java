package com.careerbridge.payment.dto.external;

/**
 * Razorpay's /v1/orders wire shapes, request and response in one file so a shape correction is a
 * single-file edit -- same convention as ai-coach-service's dto/external package.
 *
 * Every field the service actually needs is a single lowercase word on the wire (id, amount,
 * currency, status, receipt), so there is no snake_case to map and no @JsonProperty anywhere.
 * Jackson 3 has FAIL_ON_UNKNOWN_PROPERTIES off, so the fields Razorpay returns that are not
 * declared here (entity, amount_paid, amount_due, attempts, notes, created_at) are simply ignored.
 *
 * Verified against https://razorpay.com/docs/api/orders/create and .../entity.
 */
public final class RazorpayOrderDtos {

    private RazorpayOrderDtos() {
    }

    /**
     * amount is an integer in PAISE, not rupees -- "for an actual amount of Rs 222.25, the value of
     * this field should be 22225". receipt is capped at 40 characters by Razorpay.
     */
    public record CreateOrderRequest(long amount, String currency, String receipt) {
    }

    /**
     * status is one of created -> attempted -> paid. The reconcile path keys off "paid".
     */
    public record OrderResponse(String id, Long amount, String currency, String status, String receipt) {

        public static final String STATUS_PAID = "paid";

        public boolean isPaid() {
            return STATUS_PAID.equalsIgnoreCase(status);
        }
    }
}
