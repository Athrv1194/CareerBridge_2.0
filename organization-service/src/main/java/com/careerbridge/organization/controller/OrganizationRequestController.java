package com.careerbridge.organization.controller;

import com.careerbridge.organization.dto.OrgRequestResponse;
import com.careerbridge.organization.dto.RejectOrgRequest;
import com.careerbridge.organization.dto.SubmitOrgRequest;
import com.careerbridge.organization.model.RequestStatus;
import com.careerbridge.organization.service.OrganizationRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * /apply is the ONLY public endpoint here, and deliberately not under /requests: api-gateway's
 * isPublicPath matches on path only, with no notion of HTTP method, so a public path forwards with
 * every identity header stripped. Making /requests public to cover this POST would also make the
 * SUPER_ADMIN GET on that same path public, leaking every applicant's name, email and phone to any
 * unauthenticated caller. See api-gateway's application.yml public-paths list.
 *
 * /apply reads no headers at all, same reason as payment-service's getPlans(): a public path
 * forwards no identity headers, so a required @RequestHeader here would 400 every request instead
 * of being usefully public.
 */
@RestController
@RequestMapping("/api/organization")
public class OrganizationRequestController {

    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final OrganizationRequestService organizationRequestService;

    public OrganizationRequestController(OrganizationRequestService organizationRequestService) {
        this.organizationRequestService = organizationRequestService;
    }

    @PostMapping("/apply")
    public ResponseEntity<OrgRequestResponse> submit(@Valid @RequestBody SubmitOrgRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organizationRequestService.submit(request));
    }

    @GetMapping("/requests")
    public ResponseEntity<List<OrgRequestResponse>> list(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestParam(required = false) RequestStatus status) {
        return ResponseEntity.ok(organizationRequestService.list(status, callerRole));
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<OrgRequestResponse> getById(
            @PathVariable Long id,
            @RequestHeader(USER_ROLE_HEADER) String callerRole) {
        return ResponseEntity.ok(organizationRequestService.getById(id, callerRole));
    }

    @PostMapping("/requests/{id}/approve")
    public ResponseEntity<OrgRequestResponse> approve(
            @PathVariable Long id,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(USER_ID_HEADER) Long callerUserId) {
        return ResponseEntity.ok(organizationRequestService.approve(id, callerRole, callerUserId));
    }

    @PostMapping("/requests/{id}/reject")
    public ResponseEntity<OrgRequestResponse> reject(
            @PathVariable Long id,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(USER_ID_HEADER) Long callerUserId,
            @Valid @RequestBody RejectOrgRequest request) {
        return ResponseEntity.ok(organizationRequestService.reject(id, request, callerRole, callerUserId));
    }
}
