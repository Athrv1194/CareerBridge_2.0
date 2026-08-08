package com.careerbridge.assessment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Payload published to RabbitMQ when an attempt is submitted
 * (exchange careerbridge.exchange, routing key assessment.completed).
 *
 * Planned consumers are recommendation-service and notification-service; neither is built yet, so
 * this is currently published into an exchange with no bindings -- messages are discarded until a
 * consumer declares its queue. That is expected, not a fault.
 *
 * This class is the contract. Consumers should declare their own copy with matching field names and
 * take the concrete type in @RabbitListener, so Spring AMQP's default TypePrecedence.INFERRED
 * resolves the payload from the method signature rather than the sender's __TypeId__ header (which
 * names this package and would not resolve on their classpath).
 *
 * All fields are flat types -- no enums -- so adding a value on this side can never hard-fail a
 * consumer's Jackson binding.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentCompletedEvent {

    private Long userId;
    private Long attemptId;
    private Long categoryId;
    private String categoryName;
    private Double categoryScorePercentage;
    private String topCareerPath;
    private Double careerMatchPercentage;
    private LocalDateTime completedAt;

    /**
     * Match percentage for EVERY career path, keyed by career name -- not just the top N, and not
     * the same thing as AssessmentResult.allCareerScoresJson (which stores only the top
     * TOP_CAREERS_TO_RECOMMEND and is what the HTTP result DTO returns).
     *
     * Added so recommendation-service can rank the full field without maintaining its own copy of
     * the career catalogue and re-deriving these numbers. Insertion-ordered (LinkedHashMap in
     * career_paths findAll() order), which consumers may rely on as a tie-break -- several careers
     * legitimately share a score, since relevance is a coarse 1.0/0.3 weight.
     *
     * Nullable in the same case topCareerPath is: an empty career_paths table yields an empty map.
     */
    private Map<String, Double> allCareerScores;
}
