package com.careerbridge.organization.controller;

import com.careerbridge.organization.dto.CreateDepartmentRequest;
import com.careerbridge.organization.dto.CreateOrganizationRequest;
import com.careerbridge.organization.dto.DepartmentResponse;
import com.careerbridge.organization.dto.OrganizationResponse;
import com.careerbridge.organization.dto.UpdateOrganizationRequest;
import com.careerbridge.organization.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Identity arrives entirely in headers injected by api-gateway after it validates the JWT; this
 * service never parses a token. The gateway strips any client-supplied copy of all three, so they
 * are trusted input here.
 *
 * Consequence: port 8087 must not be publicly reachable. Anyone who can reach it directly can send
 * X-User-Role: SUPER_ADMIN and manage every tenant. That is a network control, not something this
 * code can enforce.
 *
 * Singular /api/organization, matching the other five services and the gateway's route predicate.
 * Gateway predicates match whole segments, so a plural mapping would 404 every request arriving
 * through the gateway.
 */
@RestController
@RequestMapping("/api/organization")
public class OrganizationController {

    // X-User-Id is deliberately not read: no operation here keys off the individual user, only off
    // their role and tenant. Requiring X-User-Role is what proves the request came through the
    // gateway authenticated, since the gateway strips the header when there is no valid token.
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_ORG_ID_HEADER = "X-User-Org-Id";

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    public ResponseEntity<OrganizationResponse> createOrganization(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @Valid @RequestBody CreateOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.createOrganization(request, callerRole));
    }

    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> getAllOrganizations(
            @RequestHeader(USER_ROLE_HEADER) String callerRole) {
        return ResponseEntity.ok(organizationService.getAllOrganizations(callerRole));
    }

    /**
     * X-User-Org-Id is required = false on every endpoint that takes it: a SUPER_ADMIN belongs to
     * no organization, so the gateway forwards no header at all for them. Marking it required
     * would 400 every SUPER_ADMIN request.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrganizationResponse> getOrganizationById(
            @PathVariable Long id,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(value = USER_ORG_ID_HEADER, required = false) Long callerOrgId) {
        return ResponseEntity.ok(organizationService.getOrganizationById(id, callerRole, callerOrgId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrganizationResponse> updateOrganization(
            @PathVariable Long id,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(value = USER_ORG_ID_HEADER, required = false) Long callerOrgId,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        return ResponseEntity.ok(
                organizationService.updateOrganization(id, request, callerRole, callerOrgId));
    }

    /** Soft delete: sets isActive = false rather than removing the row and its departments. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateOrganization(
            @PathVariable Long id,
            @RequestHeader(USER_ROLE_HEADER) String callerRole) {
        organizationService.deactivateOrganization(id, callerRole);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/departments")
    public ResponseEntity<DepartmentResponse> createDepartment(
            @PathVariable Long id,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(value = USER_ORG_ID_HEADER, required = false) Long callerOrgId,
            @Valid @RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.createDepartment(id, request, callerRole, callerOrgId));
    }

    @GetMapping("/{id}/departments")
    public ResponseEntity<List<DepartmentResponse>> getDepartmentsByOrg(
            @PathVariable Long id,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestHeader(value = USER_ORG_ID_HEADER, required = false) Long callerOrgId) {
        return ResponseEntity.ok(organizationService.getDepartmentsByOrg(id, callerRole, callerOrgId));
    }
}
