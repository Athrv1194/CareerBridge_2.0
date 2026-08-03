package com.careerbridge.recruiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregate placement outcomes, for an organization (ORG_ADMIN/SUPER_ADMIN) or for a recruiter's
 * own jobs (RECRUITER).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlacementStatsResponse {

    /**
     * How many students the numbers below were computed over.
     *
     * Not in the original task spec, and deliberately added. The org-scoped path gets its student
     * roster from prs-service, which returns an empty list for THREE different situations that are
     * otherwise indistinguishable: prs-service being down, a genuinely empty college, and an
     * ORG_ADMIN whose token carries no X-User-Org-Id. Without this field all three render as a
     * screen of zeros that reads as "nobody here got placed". A zero here says "we counted nobody"
     * instead, which is the honest statement.
     *
     * Zero for the recruiter-scoped endpoint, which has no roster concept.
     */
    private long totalStudentsInScope;

    private long totalApplications;

    /** Applications currently in ApplicationStatus.OFFERED, whatever the student decided after. */
    private long offersExtended;

    private long offersAccepted;

    private long offersDeclined;

    /**
     * Distinct students with an accepted offer / distinct students with at least one application,
     * as a percentage rounded to 2 decimals. 0.0 when nobody has applied.
     *
     * Counts STUDENTS, not applications, on purpose: a student who applies to twenty jobs and
     * accepts one is fully placed, and an application-level ratio would score that as 5%.
     */
    private double placementRate;

    /** Mean CTC across ACCEPTED offers only, in LPA. Null when there are none -- never 0. */
    private BigDecimal averageCtc;

    /** Highest ACCEPTED offer, in LPA. Null when there are none. */
    private BigDecimal highestCtc;

    /** Distinct company names with at least one accepted offer, capped at 5. */
    private List<String> topCompanies;
}
