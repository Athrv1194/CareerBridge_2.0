package com.careerbridge.assessment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

// Published to careerbridge.exchange with routing key assessment.completed.
// Consumers declare their own copy of this class -- TypePrecedence.INFERRED resolves from
// the @RabbitListener signature, not the __TypeId__ header (which names this package).
// All fields are flat types so adding a value here never hard-fails a consumer's Jackson binding.
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

    // Full career map, not just top-N -- differs from AssessmentResult.allCareerScoresJson which
    // stores only TOP_CAREERS_TO_RECOMMEND. Null when career_paths table is empty.
    private Map<String, Double> allCareerScores;
}
