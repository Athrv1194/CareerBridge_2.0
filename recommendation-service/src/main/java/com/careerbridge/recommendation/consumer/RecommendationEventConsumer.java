package com.careerbridge.recommendation.consumer;

import com.careerbridge.recommendation.constants.RecommendationConstants;
import com.careerbridge.recommendation.event.AssessmentCompletedEvent;
import com.careerbridge.recommendation.repository.RecommendationRepository;
import com.careerbridge.recommendation.service.RecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class RecommendationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEventConsumer.class);

    private final RecommendationService recommendationService;
    private final RecommendationRepository recommendationRepository;

    public RecommendationEventConsumer(RecommendationService recommendationService,
                                       RecommendationRepository recommendationRepository) {
        this.recommendationService = recommendationService;
        this.recommendationRepository = recommendationRepository;
    }

    /**
     * Turns a finished assessment into a career recommendation, so the student sees one without
     * asking for it.
     *
     * The parameter is the concrete event type on purpose: Spring AMQP's default
     * TypePrecedence.INFERRED resolves the payload from this signature, so the sender's __TypeId__
     * header (which names assessment-service's package) is never read. Widening this to Object or
     * Message would fall back to that header and blow up with ClassNotFoundException.
     *
     * Not @Transactional -- generateRecommendation carries its own. The existsByAssessmentAttemptId
     * guard is a best-effort fast path with an unavoidable TOCTOU window; the unique constraint on
     * Recommendation.assessmentAttemptId is what actually guarantees idempotency, and it surfaces
     * here as the DataIntegrityViolationException caught below. Keeping this method outside the
     * transaction also means the catch is not swallowing an exception on an already
     * rollback-marked transaction.
     */
    @RabbitListener(queues = RecommendationConstants.QUEUE_NAME)
    public void onAssessmentCompleted(AssessmentCompletedEvent event) {
        try {
            // categoryName and categoryScorePercentage are guarded alongside the ids because both
            // are dereferenced unboxed during scoring -- a null there is an NPE, not a bad result.
            if (event == null
                    || event.getUserId() == null
                    || event.getAttemptId() == null
                    || event.getCategoryName() == null
                    || event.getCategoryScorePercentage() == null) {
                log.warn("Ignoring incomplete {} payload: {}",
                        RecommendationConstants.ROUTING_KEY_ASSESSMENT_COMPLETED, event);
                return;
            }

            if (recommendationRepository.existsByAssessmentAttemptId(event.getAttemptId())) {
                log.info("Recommendation already exists for attemptId={}, skipping",
                        event.getAttemptId());
                return;
            }

            recommendationService.generateRecommendation(event);

            log.info("Generated recommendation for userId={} from attemptId={}",
                    event.getUserId(), event.getAttemptId());
        } catch (Exception ex) {
            // Fail-soft: rethrowing would requeue the message and spin the listener forever on a
            // payload that will never succeed. A missing recommendation is recoverable -- the
            // student can retake the assessment -- while an infinite redelivery loop takes the
            // consumer down for every other student too.
            log.error("Failed to handle {} for attemptId={}: {}",
                    RecommendationConstants.ROUTING_KEY_ASSESSMENT_COMPLETED,
                    event == null ? null : event.getAttemptId(),
                    ex.getMessage());
        }
    }
}
