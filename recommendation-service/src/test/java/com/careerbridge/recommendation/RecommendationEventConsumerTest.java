package com.careerbridge.recommendation;

import com.careerbridge.recommendation.consumer.RecommendationEventConsumer;
import com.careerbridge.recommendation.event.AssessmentCompletedEvent;
import com.careerbridge.recommendation.repository.RecommendationRepository;
import com.careerbridge.recommendation.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The @RabbitListener annotation is irrelevant to a direct method call, so these drive the
 * listener method itself -- no embedded broker, no @SpringBootTest.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationEventConsumerTest {

    private static final Long USER_ID = 42L;
    private static final Long ATTEMPT_ID = 7L;

    @Mock private RecommendationService recommendationService;
    @Mock private RecommendationRepository recommendationRepository;

    @InjectMocks private RecommendationEventConsumer consumer;

    private AssessmentCompletedEvent event;

    @BeforeEach
    void setUp() {
        event = AssessmentCompletedEvent.builder()
                .userId(USER_ID)
                .attemptId(ATTEMPT_ID)
                .categoryId(1L)
                .categoryName("Programming Fundamentals")
                .categoryScorePercentage(46.67)
                .topCareerPath("Full Stack Developer")
                .careerMatchPercentage(14.0)
                .completedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("assessment event: generates a recommendation for the completed attempt")
    void assessmentCompletedEvent_GeneratesRecommendation() {
        when(recommendationRepository.existsByAssessmentAttemptId(ATTEMPT_ID)).thenReturn(false);

        consumer.onAssessmentCompleted(event);

        verify(recommendationService).generateRecommendation(event);
    }

    @Test
    @DisplayName("assessment event: a redelivered event does not generate a second recommendation")
    void assessmentCompletedEvent_DuplicateAttemptId_SkipsGeneration() {
        when(recommendationRepository.existsByAssessmentAttemptId(ATTEMPT_ID)).thenReturn(true);

        consumer.onAssessmentCompleted(event);

        verify(recommendationService, never()).generateRecommendation(any());
    }

    @Test
    @DisplayName("assessment event: a failing service is swallowed, so the listener cannot spin on redelivery")
    void assessmentCompletedEvent_ServiceThrows_DoesNotRethrow() {
        when(recommendationRepository.existsByAssessmentAttemptId(ATTEMPT_ID)).thenReturn(false);
        doThrow(new RuntimeException("db down"))
                .when(recommendationService).generateRecommendation(any());

        assertDoesNotThrow(() -> consumer.onAssessmentCompleted(event));
    }

    @Test
    @DisplayName("assessment event: a payload with no userId is ignored rather than throwing")
    void assessmentCompletedEvent_MissingUserId_Ignored() {
        AssessmentCompletedEvent malformed = AssessmentCompletedEvent.builder()
                .attemptId(ATTEMPT_ID)
                .categoryName("Programming Fundamentals")
                .categoryScorePercentage(46.67)
                .build();

        assertDoesNotThrow(() -> consumer.onAssessmentCompleted(malformed));

        verify(recommendationService, never()).generateRecommendation(any());
    }

    @Test
    @DisplayName("assessment event: a null score is ignored -- it would unbox to an NPE while scoring")
    void assessmentCompletedEvent_MissingCategoryScore_Ignored() {
        AssessmentCompletedEvent malformed = AssessmentCompletedEvent.builder()
                .userId(USER_ID)
                .attemptId(ATTEMPT_ID)
                .categoryName("Programming Fundamentals")
                .categoryScorePercentage(null)
                .build();

        assertDoesNotThrow(() -> consumer.onAssessmentCompleted(malformed));

        verify(recommendationService, never()).generateRecommendation(any());
    }
}
