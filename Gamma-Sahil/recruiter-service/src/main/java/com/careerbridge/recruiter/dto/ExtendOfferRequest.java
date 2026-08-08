package com.careerbridge.recruiter.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A RECRUITER extending an offer on an application against a job they own.
 *
 * The CTC is required, not optional: an offer with no compensation figure is not an offer, and the
 * whole point of this patch is that the platform previously recorded ApplicationStatus.OFFERED
 * with no amount attached.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtendOfferRequest {

    /**
     * In LPA (lakhs per annum) -- see JobApplication.offeredCtc.
     *
     * inclusive = false rejects 0 as well as negatives: a zero-CTC offer is a data-entry mistake,
     * not an unpaid role, and an unpaid role would not be recorded as an offer with a number.
     */
    @NotNull(message = "Offered CTC is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Offered CTC must be greater than zero")
    private BigDecimal offeredCtc;
}
