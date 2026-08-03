package com.careerbridge.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * The pricing-page shape. features arrives from the database as a JSON array string and leaves
 * here as a real array, so the frontend never parses JSON out of a JSON field.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanResponse {

    private Long id;
    private String planName;
    private String description;
    private BigDecimal price;
    private String currency;
    private String billingCycle;
    private List<String> features;
}
