package com.careerbridge.assessment.controller;

import com.careerbridge.assessment.dto.AdminQuestionRequest;
import com.careerbridge.assessment.dto.AdminQuestionResponse;
import com.careerbridge.assessment.service.AdminQuestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ADMIN MODULE: question bank management for SUPER_ADMIN and ORG_ADMIN.
 *
 * callerRole arrives in X-User-Role, injected by api-gateway after it validates the JWT -- the same
 * mechanism AssessmentController uses for X-User-Id. This is the first endpoint in assessment-service
 * to read the role header; no gateway change was needed for it, because
 * GatewayIdentityRequestWrapper.MANAGED_HEADERS already covers X-User-Role and /api/assessment/**
 * was never a public path.
 *
 * No X-User-Id and no X-User-Org-Id: the question bank is platform-wide content, not tenant-owned,
 * so there is nothing to scope an admin to. Every admin with the role manages the same bank.
 *
 * No RBAC here. AdminQuestionService.requireAdmin is the single choke point, matching every other
 * service in this project.
 *
 * Note what these responses carry that the student-facing ones never do: option weights.
 * AdminQuestionResponse embeds AdminOptionResponse, which exposes weight -- a client that can see
 * weights knows the highest-scoring answer to every question. That is correct for an admin editing
 * the bank and would be a scoring hole on any student endpoint.
 */
@RestController
@RequestMapping("/api/assessment/admin")
public class AdminQuestionController {

    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final AdminQuestionService adminQuestionService;

    public AdminQuestionController(AdminQuestionService adminQuestionService) {
        this.adminQuestionService = adminQuestionService;
    }

    /** categoryId is optional: omitted returns the whole bank, including retired questions. */
    @GetMapping("/questions")
    public ResponseEntity<List<AdminQuestionResponse>> listQuestions(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(adminQuestionService.listQuestions(callerRole, categoryId));
    }

    @GetMapping("/questions/{id}")
    public ResponseEntity<AdminQuestionResponse> getQuestion(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long id) {
        return ResponseEntity.ok(adminQuestionService.getQuestion(callerRole, id));
    }

    /**
     * @Valid runs before the service is entered, so a body with fewer than 2 options, a blank
     * question, or a weight outside 0..3 never reaches the exactly-one-max-weight check.
     */
    @PostMapping("/questions")
    public ResponseEntity<AdminQuestionResponse> addQuestion(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @Valid @RequestBody AdminQuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminQuestionService.addQuestion(callerRole, request));
    }

    /** PUT, not PATCH: the body declares the question's whole state, options included. */
    @PutMapping("/questions/{id}")
    public ResponseEntity<AdminQuestionResponse> editQuestion(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long id,
            @Valid @RequestBody AdminQuestionRequest request) {
        return ResponseEntity.ok(adminQuestionService.editQuestion(callerRole, id, request));
    }

    /**
     * 204 with no body. PATCH rather than PUT because this flips one field, and the service returns
     * void so no response DTO is built for a response that carries none.
     */
    @PatchMapping("/questions/{id}/activate")
    public ResponseEntity<Void> activateQuestion(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long id) {
        adminQuestionService.activateQuestion(callerRole, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Soft retire, never a hard delete: historical AttemptAnswer rows keep pointing at a real
     * question. There is deliberately no DELETE endpoint on this controller.
     */
    @PatchMapping("/questions/{id}/deactivate")
    public ResponseEntity<Void> deactivateQuestion(
            @RequestHeader(USER_ROLE_HEADER) String callerRole,
            @PathVariable Long id) {
        adminQuestionService.deactivateQuestion(callerRole, id);
        return ResponseEntity.noContent().build();
    }
}
