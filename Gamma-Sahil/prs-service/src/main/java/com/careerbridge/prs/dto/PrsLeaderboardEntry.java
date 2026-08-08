package com.careerbridge.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of GET /api/prs/leaderboard.
 *
 * rank is 1-based and derived from position in the ordered result, not stored -- it is only
 * meaningful relative to the list it came from, and that list differs by caller: a SUPER_ADMIN sees
 * a global ranking, an ORG_ADMIN sees a ranking within their own organization only. Persisting a
 * rank column would make one of those two wrong.
 *
 * Deliberately carries no name or email. This service holds neither, and joining student-service
 * for them would turn a leaderboard read into N synchronous calls; the client already has the
 * student directory if it needs to resolve ids to names.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrsLeaderboardEntry {

    /** 1-based position within this response, highest totalScore first. */
    private Integer rank;

    private Long studentId;

    private Double totalScore;

    private String grade;
}
