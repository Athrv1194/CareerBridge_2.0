package com.careerbridge.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Everything the browser needs to open the Razorpay Checkout.js modal.
 *
 * keyId is the PUBLIC Razorpay key and is meant to reach the browser. The key SECRET is never in
 * this DTO, never logged, and never leaves the server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponse {

    private Long paymentId;
    private String razorpayOrderId;

    /** Integer paise, which is what Checkout.js expects in its `amount` option. */
    private Long amountPaise;

    /** The same amount in rupees, for display only. */
    private BigDecimal amount;

    private String currency;
    private String keyId;
    private String planName;
    private String planDescription;
}
