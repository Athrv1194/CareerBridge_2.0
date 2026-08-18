package com.careerbridge.auth.controller;

import com.careerbridge.auth.dto.AssignDepartmentRequest;
import com.careerbridge.auth.dto.UserSummaryResponse;
import com.careerbridge.auth.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service actions on the caller's OWN record. No path variable, ever -- the caller is always
 * themselves (X-User-Id, gateway-injected, never client-supplied). No class-level @RequestMapping,
 * same shape as OrganizationJoinRequestController's /api/auth/me/... routes: these coexist with
 * AdminUserController's /api/auth/admin/... routes for the same underlying data, so the two can't
 * share a class-level prefix without one distorting the other's paths.
 */
@RestController
public class UserProfileController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final AdminUserService adminUserService;

    public UserProfileController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<UserSummaryResponse> getOwnProfile(
            @RequestHeader(USER_ID_HEADER) Long callerId) {
        return ResponseEntity.ok(adminUserService.getOwnProfile(callerId));
    }

    /** Body's department may be null or blank to unassign. Requires the caller already have an organization. */
    @PatchMapping("/api/auth/me/department")
    public ResponseEntity<UserSummaryResponse> assignOwnDepartment(
            @RequestHeader(USER_ID_HEADER) Long callerId,
            @Valid @RequestBody AssignDepartmentRequest request) {
        return ResponseEntity.ok(adminUserService.assignOwnDepartment(callerId, request.getDepartment()));
    }
}
