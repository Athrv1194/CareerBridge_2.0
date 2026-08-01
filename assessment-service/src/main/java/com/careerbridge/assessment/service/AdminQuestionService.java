package com.careerbridge.assessment.service;

import com.careerbridge.assessment.dto.AdminQuestionRequest;
import com.careerbridge.assessment.dto.AdminQuestionResponse;

import java.util.List;

/**
 * Question bank management for SUPER_ADMIN and ORG_ADMIN.
 *
 * Every method here reaches questions regardless of isActive -- an admin who cannot see a retired
 * question cannot reactivate it. That is the opposite of the student-facing AssessmentService, whose
 * every query filters isActive.
 */
public interface AdminQuestionService {

    AdminQuestionResponse addQuestion(String callerRole, AdminQuestionRequest request);

    AdminQuestionResponse editQuestion(String callerRole, Long questionId, AdminQuestionRequest request);

    AdminQuestionResponse getQuestion(String callerRole, Long questionId);

    /** categoryId is nullable -- null returns every question, including inactive ones. */
    List<AdminQuestionResponse> listQuestions(String callerRole, Long categoryId);

    /**
     * void, not a response body: the endpoint answers 204, so building a DTO would cost a category
     * lookup and an options query for something nobody sees.
     */
    void activateQuestion(String callerRole, Long questionId);

    void deactivateQuestion(String callerRole, Long questionId);
}
