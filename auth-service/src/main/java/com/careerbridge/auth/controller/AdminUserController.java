package com.careerbridge.auth.controller;

import com.careerbridge.auth.dto.AdminStatsResponse;
import com.careerbridge.auth.dto.AssignDepartmentRequest;
import com.careerbridge.auth.dto.ChangeRoleRequest;
import com.careerbridge.auth.dto.LinkOrganizationRequest;
import com.careerbridge.auth.dto.UserSummaryResponse;
import com.careerbridge.auth.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Platform administration: user management and stats for SUPER_ADMIN and ORG_ADMIN.
 *
 * Identity arrives in headers injected by api-gateway after it validates the JWT, exactly as in
 * every other service -- this controller never touches a token, and holds no authorization logic of
 * its own. Every role check lives in AdminUserService.
 *
 * IMPORTANT, and the reason this is not purely additive: /api/auth/** was a wildcard entry in
 * api-gateway's gateway.public-paths. A public path is forwarded with NO identity headers at all
 * (JwtAuthenticationFilter wraps it with a null identity to strip any client-supplied copy), so
 * every request here would have arrived with no X-User-Role and 400'd on the required header --
 * unreachable, not insecure. That wildcard is narrowed to the four genuinely public auth endpoints
 * in the same change; see api-gateway/src/main/resources/application.yml.
 *
 * auth-service's own SecurityConfig needs nothing: it permits only register/login/refresh and falls
 * through to .anyRequest().authenticated(), which its own JwtAuthenticationFilter satisfies from the
 * forwarded Authorization header.
 */
@RestController
@RequestMapping("/api/auth/admin")
public class AdminUserController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_ORG_ID_HEADER = "X-User-Org-Id";

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * X-User-Org-Id is required = false on every endpoint that takes it, and must stay that way: a
     * SUPER_ADMIN belongs to no organization, so the gateway forwards no header for them at all.
     * Marking it required would 400 every SUPER_ADMIN request before the service is reached --
     * organization-service's logged lesson.
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryResponse>> listUsers(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(value = USER_ORG_ID_HEADER, required = false) Long callerOrgId,
            // Optional ?role=STUDENT filter. A String, not the Role enum: binding the enum here
            // would make an unknown value a MethodArgumentTypeMismatchException reported as a
            // generic 400, where the service can name the offending value instead. Same reason
            // ChangeRoleRequest.role is a String.
            @RequestParam(required = false) String role) {
        return ResponseEntity.ok(adminUserService.listUsers(callerRole, callerOrgId, role));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserSummaryResponse> getUserById(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(value = USER_ORG_ID_HEADER, required = false) Long callerOrgId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(adminUserService.getUserById(callerRole, callerOrgId, userId));
    }

    /**
     * X-User-Id is read here purely so the service can refuse a self-role-change. The last
     * SUPER_ADMIN demoting themselves would leave the platform with no administrator and no API
     * route back.
     */
    @PatchMapping("/users/{userId}/role")
    public ResponseEntity<UserSummaryResponse> changeUserRole(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(USER_ID_HEADER) Long callerId,
            @PathVariable Long userId,
            @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(
                adminUserService.changeUserRole(callerRole, callerId, userId, request.getRole()));
    }

    /**
     * SUPER_ADMIN only. This is the only way an existing user's organizationId ever changes -- see
     * AdminUserService.linkOrganization. Body's organizationId may be null to unlink.
     */
    @PatchMapping("/users/{userId}/organization")
    public ResponseEntity<UserSummaryResponse> linkOrganization(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long userId,
            @RequestBody LinkOrganizationRequest request) {
        return ResponseEntity.ok(
                adminUserService.linkOrganization(callerRole, userId, request.getOrganizationId()));
    }

    /** X-User-Id read for the same self-lockout guard as changeUserRole. */
    @PatchMapping("/users/{userId}/deactivate")
    public ResponseEntity<UserSummaryResponse> deactivateUser(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(USER_ID_HEADER) Long callerId,
            @RequestHeader(value = USER_ORG_ID_HEADER, required = false) Long callerOrgId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(
                adminUserService.deactivateUser(callerRole, callerId, callerOrgId, userId));
    }

    /**
     * No self-guard needed: a deactivated user cannot log in, so they can never be the caller
     * reactivating themselves.
     */
    @PatchMapping("/users/{userId}/activate")
    public ResponseEntity<UserSummaryResponse> activateUser(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(value = USER_ORG_ID_HEADER, required = false) Long callerOrgId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(adminUserService.activateUser(callerRole, callerOrgId, userId));
    }

    /**
     * ORG_ADMIN (own organization only) or SUPER_ADMIN -- unlike the organization endpoint above, an
     * ORG_ADMIN is allowed here because a department sits inside the tenant they already administer.
     * Body's department may be null or blank to unassign.
     */
    @PatchMapping("/users/{userId}/department")
    public ResponseEntity<UserSummaryResponse> assignDepartment(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(value = USER_ORG_ID_HEADER, required = false) Long callerOrgId,
            @PathVariable Long userId,
            @Valid @RequestBody AssignDepartmentRequest request) {
        return ResponseEntity.ok(adminUserService.assignDepartment(
                callerRole, callerOrgId, userId, request.getDepartment()));
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getPlatformStats(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(value = USER_ORG_ID_HEADER, required = false) Long callerOrgId) {
        return ResponseEntity.ok(adminUserService.getPlatformStats(callerRole, callerOrgId));
    }
}
