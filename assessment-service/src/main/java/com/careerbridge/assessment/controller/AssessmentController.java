package com.careerbridge.assessment.controller;

import com.careerbridge.assessment.dto.AssessmentRequest;
import com.careerbridge.assessment.dto.AssessmentResponse;
import com.careerbridge.assessment.dto.AssessmentResultDto;
import com.careerbridge.assessment.dto.CategoryDto;
import com.careerbridge.assessment.dto.QuestionDto;
import com.careerbridge.assessment.dto.SubmitAnswerRequest;
import com.careerbridge.assessment.service.AssessmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * userId arrives in X-User-Id, forwarded by the API Gateway after it validates the JWT -- this
 * service never parses tokens itself.
 *
 * That makes the header a trusted input, so port 8083 must not be publicly reachable: anyone who
 * can hit it directly can set the header to any value and submit assessments as that student. This
 * is a network control (security group / bind address), not something the code can enforce.
 *
 * Note what the responses never carry: option weights. GET /questions and the start response both
 * return OptionDto, which has no weight field -- a client that could see weights would know the
 * highest-scoring answer to every question.
 */
@RestController
@RequestMapping("/api/assessment")
public class AssessmentController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    /** Catalogue browse. No header: the category list is identical for every student. */
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> getCategories() {
        return ResponseEntity.ok(assessmentService.getCategories());
    }

    /** Preview a category's questions without starting an attempt. No header, no weights. */
    @GetMapping("/questions/{categoryId}")
    public ResponseEntity<List<QuestionDto>> getQuestions(@PathVariable Long categoryId) {
        return ResponseEntity.ok(assessmentService.getQuestions(categoryId));
    }

    @PostMapping("/attempt/start")
    public ResponseEntity<AssessmentResponse> startAttempt(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @Valid @RequestBody AssessmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assessmentService.startAttempt(userId, request));
    }

    /** Returns the scored result on the same round trip -- no follow-up GET needed. */
    @PostMapping("/attempt/submit")
    public ResponseEntity<AssessmentResultDto> submitAttempt(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @Valid @RequestBody SubmitAnswerRequest request) {
        return ResponseEntity.ok(assessmentService.submitAttempt(userId, request));
    }

    @GetMapping("/result/{attemptId}")
    public ResponseEntity<AssessmentResultDto> getResult(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @PathVariable Long attemptId) {
        return ResponseEntity.ok(assessmentService.getResult(userId, attemptId));
    }
}
