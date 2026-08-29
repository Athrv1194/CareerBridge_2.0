package com.careerbridge.recruiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One department's slice of PlacementStatsResponse. Same definitions as the top-level figures --
 * both come from the same computation, so a department row can never disagree with the total.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentPlacementStatsDto {

    /**
     * Null for the students who have no department assigned. Deliberately a null entry rather than
     * a synthesised "Unassigned" label: a real department could legitimately be named that, and the
     * caller should be free to render the null case however it likes -- exactly how a null
     * department is handled on CandidateResponse.
     */
    private String department;

    /** Students of this department present in the roster the stats were computed over. */
    private long studentsInScope;

    private long totalApplications;

    private long offersExtended;

    private long offersAccepted;

    private long offersDeclined;

    /** Distinct placed students / distinct applicants IN THIS DEPARTMENT, 2dp. */
    private double placementRate;

    /** Mean CTC across this department's accepted offers, in LPA. Null when there are none. */
    private BigDecimal averageCtc;

    /** Highest accepted offer in this department, in LPA. Null when there are none. */
    private BigDecimal highestCtc;
}
