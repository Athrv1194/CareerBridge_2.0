package com.careerbridge.auth.service;

import com.careerbridge.auth.dto.AdminStatsResponse;
import com.careerbridge.auth.dto.UserSummaryResponse;

import java.util.List;

/**
 * Platform user management for SUPER_ADMIN and ORG_ADMIN.
 *
 * Every method takes callerRole as its first argument and enforces authorization itself. That is
 * deliberate and matches organization-service, roadmap-service and prs-service: the gateway knows
 * who the caller is but nothing about what they may reach, and a controller that decided this would
 * put the rule one layer away from the data it protects.
 *
 * callerOrgId is nullable throughout -- a SUPER_ADMIN belongs to no organization, so the gateway
 * forwards no X-User-Org-Id header for them.
 */
public interface AdminUserService {

    /**
     * SUPER_ADMIN sees every active user; ORG_ADMIN sees only their own organization's.
     *
     * roleFilter is optional: null or blank means every role. An unrecognised value is a 400 rather
     * than an empty list, so a typo'd filter is not mistaken for "no users match".
     */
    List<UserSummaryResponse> listUsers(String callerRole, Long callerOrgId, String roleFilter);

    UserSummaryResponse getUserById(String callerRole, Long callerOrgId, Long targetUserId);

    /**
     * SUPER_ADMIN only -- an ORG_ADMIN who could grant roles could grant themselves SUPER_ADMIN.
     *
     * callerId is taken so the caller cannot change their OWN role: the last SUPER_ADMIN demoting
     * themselves leaves the platform with no administrator and no API path back.
     */
    UserSummaryResponse changeUserRole(String callerRole, Long callerId, Long targetUserId, String newRole);

    /** Takes callerId for the same self-lockout reason as changeUserRole. */
    UserSummaryResponse deactivateUser(String callerRole, Long callerId, Long callerOrgId, Long targetUserId);

    /**
     * SUPER_ADMIN only -- an ORG_ADMIN able to move a user into an organization could move one into
     * their OWN organization, which is a privilege escalation dressed up as a data edit. This is
     * the only way an existing user's organizationId ever changes: POST /auth/register sets it once
     * at signup and nothing else in the system has ever been able to touch it since.
     *
     * organizationId may be null, which unlinks the user rather than being rejected as invalid.
     */
    UserSummaryResponse linkOrganization(String callerRole, Long targetUserId, Long organizationId);

    UserSummaryResponse activateUser(String callerRole, Long callerOrgId, Long targetUserId);

    /**
     * Assigns a user to a department within their own organization. ORG_ADMIN (own organization
     * only) or SUPER_ADMIN -- unlike changeUserRole and linkOrganization, an ORG_ADMIN is allowed
     * here because a department is a subdivision of the organization they already administer, so
     * nothing they can reach with it lies outside their existing tenant.
     *
     * department may be null or blank, which UNASSIGNS the user; both normalise to a stored null so
     * a blank string never becomes a distinct department that groups separately from unassigned.
     *
     * Refuses a user who belongs to no organization -- a department is meaningless without one.
     */
    UserSummaryResponse assignDepartment(String callerRole, Long callerOrgId, Long targetUserId,
                                         String department);

    /** Counts are platform-wide for a SUPER_ADMIN and organization-scoped for an ORG_ADMIN. */
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
