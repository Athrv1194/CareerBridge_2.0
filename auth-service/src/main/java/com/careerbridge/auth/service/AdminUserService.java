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

    // Platform-wide for SUPER_ADMIN, org-scoped for ORG_ADMIN.
    AdminStatsResponse getPlatformStats(String callerRole, Long callerOrgId);
}
