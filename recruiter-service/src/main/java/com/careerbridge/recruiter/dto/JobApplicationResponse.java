package com.careerbridge.recruiter.dto;

import com.careerbridge.recruiter.model.enums.ApplicationStatus;
import com.careerbridge.recruiter.model.enums.OfferOutcome;
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
public class JobApplicationResponse {

    private Long id;
    private Long jobId;
    private String jobTitle;
    private Long studentId;
    private ApplicationStatus status;

    /**
     * Offer detail, all three null until a recruiter extends one. Returned on every application
     * response rather than a separate endpoint: a student listing their applications needs to see
     * which one carries an offer, and status alone does not carry the amount.
     *
     * offeredCtc is in LPA -- see JobApplication.offeredCtc.
     */
    private BigDecimal offeredCtc;
    private LocalDateTime offerDate;
    private OfferOutcome offerOutcome;

    private LocalDateTime appliedAt;
    private LocalDateTime updatedAt;

    /** True once the student has attached a résumé to this specific application. */
    private boolean hasResume;
}
