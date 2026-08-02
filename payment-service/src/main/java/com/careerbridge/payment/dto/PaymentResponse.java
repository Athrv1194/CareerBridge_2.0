package com.careerbridge.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long id;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private Long amountPaise;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String planName;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
