package com.careerbridge.auth.service;

import com.careerbridge.auth.dto.AdminStatsResponse;
import com.careerbridge.auth.dto.UserSummaryResponse;

import java.util.List;

// RBAC enforced in the service layer, not the controller. callerOrgId is nullable (SUPER_ADMIN has no org).
public interface AdminUserService {

    // roleFilter null/blank = all roles. Unrecognised value → 400 (not silent empty list).
    List<UserSummaryResponse> listUsers(String callerRole, Long callerOrgId, String roleFilter);

    UserSummaryResponse getUserById(String callerRole, Long callerOrgId, Long targetUserId);

    // SUPER_ADMIN only. callerId passed to block self-demotion (last SUPER_ADMIN can't lock themselves out).
    UserSummaryResponse changeUserRole(String callerRole, Long callerId, Long targetUserId, String newRole);

    // callerId passed for same self-lockout reason as changeUserRole.
    UserSummaryResponse deactivateUser(String callerRole, Long callerId, Long callerOrgId, Long targetUserId);

    // SUPER_ADMIN only. organizationId null = unlink. Only path besides registration that changes User.organizationId.
    UserSummaryResponse linkOrganization(String callerRole, Long targetUserId, Long organizationId);

    UserSummaryResponse activateUser(String callerRole, Long callerOrgId, Long targetUserId);

    // Assigns a user to a department within their own organization. ORG_ADMIN (own org only) or
    // SUPER_ADMIN. department null/blank unassigns; both normalise to stored null. Refuses a user
    // with no organization.
    UserSummaryResponse assignDepartment(String callerRole, Long callerOrgId, Long targetUserId,
                                         String department);

    // Platform-wide for SUPER_ADMIN, org-scoped for ORG_ADMIN.
    AdminStatsResponse getPlatformStats(String callerRole, Long callerOrgId);

    /**
     * Self-service: a caller reading their OWN record. No role or org check -- unlike every other
     * method here, a user is always entitled to read their own data, whoever they are.
     */
    UserSummaryResponse getOwnProfile(Long callerId);

    /**
     * Self-service: a caller assigns or clears their OWN department. Same normalisation and
     * organizationId guard as assignDepartment, but no requireAdmin/requireSameOrgIfOrgAdmin --
     * acting on your own record needs no authorization beyond being that record's owner.
     */
    UserSummaryResponse assignOwnDepartment(Long callerId, String department);
}
