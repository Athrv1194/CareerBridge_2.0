package com.careerbridge.recruiter.dto;

import com.careerbridge.recruiter.model.enums.OfferOutcome;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The STUDENT's answer to an offer. Only the student who owns the application may send this --
 * nobody accepts a job on someone else's behalf.
 *
 * Typed as the enum rather than a String, so an unrecognised value is a 400 from Jackson before it
 * reaches the service rather than a hand-rolled string comparison inside it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferResponseRequest {

    @NotNull(message = "Offer outcome is required")
    private OfferOutcome outcome;
}
