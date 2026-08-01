package com.careerbridge.recruiter.controller;

import com.careerbridge.recruiter.dto.CompanyResponse;
import com.careerbridge.recruiter.dto.CreateCompanyRequest;
import com.careerbridge.recruiter.dto.UpdateCompanyRequest;
import com.careerbridge.recruiter.service.CompanyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * service never parses a token. The gateway strips any client-supplied copy of those headers,
 * which is what makes them trustworthy here.
 *
 * Consequence: port 8090 must not be publicly reachable. Anyone who can reach it directly can send
 * X-User-Role: RECRUITER and post jobs as any user. That is a network control, not something this
 * code can enforce -- docker-compose publishes no host port for this service.
 *
 * No RBAC here: every authorization decision lives in CompanyService.
 */
@RestController
@RequestMapping("/api/recruiter/companies")
public class RecruiterCompanyController {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final CompanyService companyService;

    public RecruiterCompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping
    public ResponseEntity<CompanyResponse> createCompany(
            @RequestHeader(USER_ID_HEADER) Long recruiterId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @Valid @RequestBody CreateCompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(companyService.createCompany(callerRole, recruiterId, request));
    }

    /** Declared before /{id} so "my" is never parsed as a company id. */
    @GetMapping("/my")
    public ResponseEntity<List<CompanyResponse>> getMyCompanies(
            @RequestHeader(USER_ID_HEADER) Long recruiterId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole) {
        return ResponseEntity.ok(companyService.getMyCompanies(callerRole, recruiterId));
    }

    /** No role header: any authenticated user may look up the company behind a job posting. */
    @GetMapping("/{id}")
    public ResponseEntity<CompanyResponse> getCompanyById(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.getCompanyById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CompanyResponse> updateCompany(
            @RequestHeader(USER_ID_HEADER) Long recruiterId,
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCompanyRequest request) {
        return ResponseEntity.ok(companyService.updateCompany(callerRole, recruiterId, id, request));
    }
}
