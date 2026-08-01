package com.careerbridge.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of GET /api/auth/admin/stats.
 *
 * Every count excludes soft-deleted users, so the six per-role figures sum to totalUsers, and
 * activeUsers equals totalUsers. They are kept as separate fields rather than collapsed because
 * the day a "deactivated users" count is added, totalUsers stops meaning "active" and the two
 * diverge -- the shape then still holds.
 *
 * The six role fields mirror auth-service's Role enum exactly: STUDENT, ORG_ADMIN,
 * PLACEMENT_OFFICER, RECRUITER, MENTOR, SUPER_ADMIN. Adding a seventh role means adding a field
 * here, or its users are counted in totalUsers and in nothing else.
 *
 * Scope depends on the caller: a SUPER_ADMIN gets platform-wide counts, an ORG_ADMIN gets counts
 * for their own organization only. Same numbers, different denominator -- the response does not
 * say which, because the caller already knows what they are.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsResponse {

    private Long totalUsers;

    private Long totalStudents;

    private Long totalOrgAdmins;

    private Long totalRecruiters;

    private Long totalMentors;

    private Long totalPlacementOfficers;

    private Long totalSuperAdmins;

    /** isDeleted = false. Equal to totalUsers today; see the class comment. */
    private Long activeUsers;
}
