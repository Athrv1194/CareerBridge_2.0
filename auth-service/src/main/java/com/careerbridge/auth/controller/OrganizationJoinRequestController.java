package com.careerbridge.auth.controller;

import com.careerbridge.auth.dto.JoinRequestResponse;
import com.careerbridge.auth.dto.SubmitJoinRequest;
import com.careerbridge.auth.service.OrganizationJoinRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Self-service organization linking, split by who the caller is:
 * - /api/auth/me/organization-requests: any authenticated STUDENT/PLACEMENT_OFFICER/MENTOR, about
 *   their own account only -- there is no userId path variable because there cannot be, the caller
 *   is always themselves (X-User-Id, gateway-injected, never client-supplied).
 * - /api/auth/admin/organization-requests: the ORG_ADMIN who owns the target organization, reviewing
 *   requests naming it. Same authorization shape as AdminUserController.
 */
@RestController
public class OrganizationJoinRequestController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_ORG_ID_HEADER = "X-User-Org-Id";

    private final OrganizationJoinRequestService joinRequestService;

    public OrganizationJoinRequestController(OrganizationJoinRequestService joinRequestService) {
        this.joinRequestService = joinRequestService;
    }

    @PostMapping("/api/auth/me/organization-requests")
    public ResponseEntity<JoinRequestResponse> submit(
            @RequestHeader(USER_ID_HEADER) Long callerId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @Valid @RequestBody SubmitJoinRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(joinRequestService.submit(callerId, callerRole, request.getOrganizationId()));
    }

    @GetMapping("/api/auth/admin/organization-requests")
    public ResponseEntity<List<JoinRequestResponse>> listForOrg(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(value = USER_ORG_ID_HEADER, required = false) Long callerOrgId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(joinRequestService.listForOrg(callerRole, callerOrgId, status));
    }

    @PatchMapping("/api/auth/admin/organization-requests/{id}/approve")
    public ResponseEntity<JoinRequestResponse> approve(
            @PathVariable Long id,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(value = USER_ORG_ID_HEADER, required = false) Long callerOrgId,
            @RequestHeader(USER_ID_HEADER) Long callerId) {
        return ResponseEntity.ok(joinRequestService.approve(id, callerRole, callerOrgId, callerId));
    }

    @PatchMapping("/api/auth/admin/organization-requests/{id}/reject")
    public ResponseEntity<JoinRequestResponse> reject(
            @PathVariable Long id,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(value = USER_ORG_ID_HEADER, required = false) Long callerOrgId,
            @RequestHeader(USER_ID_HEADER) Long callerId) {
        return ResponseEntity.ok(joinRequestService.reject(id, callerRole, callerOrgId, callerId));
    }
}
