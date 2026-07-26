package com.careerbridge.assessment.service;

import com.careerbridge.assessment.dto.AssessmentRequest;
import com.careerbridge.assessment.dto.AssessmentResponse;
import com.careerbridge.assessment.dto.AssessmentResultDto;
import com.careerbridge.assessment.dto.CategoryDto;
import com.careerbridge.assessment.dto.QuestionDto;
import com.careerbridge.assessment.dto.SubmitAnswerRequest;

import java.util.List;

public interface AssessmentService {

    List<CategoryDto> getCategories();

    List<QuestionDto> getQuestions(Long categoryId);

    AssessmentResponse startAttempt(Long userId, AssessmentRequest request);

    AssessmentResultDto submitAttempt(Long userId, SubmitAnswerRequest request);

    AssessmentResultDto getResult(Long userId, Long attemptId);
}
