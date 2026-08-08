package com.careerbridge.recommendation.service;

import com.careerbridge.recommendation.constants.CareerCatalog;
import com.careerbridge.recommendation.constants.RecommendationConstants;
import com.careerbridge.recommendation.dto.CareerPathDto;
import com.careerbridge.recommendation.dto.CareerRankingDto;
import com.careerbridge.recommendation.dto.RecommendationReasonDto;
import com.careerbridge.recommendation.dto.RecommendationResponse;
import com.careerbridge.recommendation.event.AssessmentCompletedEvent;
import com.careerbridge.recommendation.event.RecommendationGeneratedEvent;
import com.careerbridge.recommendation.exception.CustomException;
import com.careerbridge.recommendation.model.CareerRanking;
import com.careerbridge.recommendation.model.Recommendation;
import com.careerbridge.recommendation.model.RecommendationReason;
import com.careerbridge.recommendation.repository.CareerRankingRepository;
import com.careerbridge.recommendation.repository.RecommendationReasonRepository;
import com.careerbridge.recommendation.repository.RecommendationRepository;
import com.careerbridge.recommendation.util.RecommendationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationServiceImpl.class);

    private final RecommendationRepository recommendationRepository;
    private final CareerRankingRepository careerRankingRepository;
    private final RecommendationReasonRepository recommendationReasonRepository;
    private final RabbitTemplate rabbitTemplate;

    public RecommendationServiceImpl(RecommendationRepository recommendationRepository,
                                     CareerRankingRepository careerRankingRepository,
                                     RecommendationReasonRepository recommendationReasonRepository,
                                     RabbitTemplate rabbitTemplate) {
        this.recommendationRepository = recommendationRepository;
        this.careerRankingRepository = careerRankingRepository;
        this.recommendationReasonRepository = recommendationReasonRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * @Transactional lives here rather than on the consumer: a half-written recommendation (parent
     * saved, rankings not) would be unrecoverable, while the consumer must stay outside any
     * transaction so its fail-soft catch is not swallowing an exception on an already
     * rollback-marked transaction.
     */
    @Override
    @Transactional
    public RecommendationResponse generateRecommendation(AssessmentCompletedEvent event) {
        List<Map.Entry<String, Double>> ranked = rankCareers(event);
        Map.Entry<String, Double> winner = ranked.get(0);

        deactivatePrevious(event.getUserId());

        Recommendation saved = recommendationRepository.save(Recommendation.builder()
                .userId(event.getUserId())
                .assessmentAttemptId(event.getAttemptId())
                .categoryId(event.getCategoryId())
                .categoryName(event.getCategoryName())
                // Read off our own rank 1, never event.careerMatchPercentage: that keeps the
                // headline figure equal to topRecommendations[0] no matter what the event said.
                .overallMatchPercentage(winner.getValue())
                .topCareerName(winner.getKey())
                .build());

        List<CareerRanking> rankings = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<String, Double> entry : ranked) {
            rankings.add(CareerRanking.builder()
                    .recommendationId(saved.getId())
                    .careerName(entry.getKey())
                    .matchPercentage(entry.getValue())
                    .rank(rank)
                    .isTopRecommendation(rank <= RecommendationConstants.TOP_CAREERS_COUNT)
                    .reasonText(RecommendationEngine.generateCareerReasonText(
                            entry.getKey(), event.getCategoryName(), entry.getValue()))
                    .build());
            rank++;
        }
        careerRankingRepository.saveAll(rankings);

        double categoryScore = event.getCategoryScorePercentage();
        RecommendationReason reason = recommendationReasonRepository.save(RecommendationReason.builder()
                .recommendationId(saved.getId())
                .strengthArea(RecommendationEngine.generateStrengthText(
                        event.getCategoryName(), categoryScore))
                .improvementArea(RecommendationEngine.generateImprovementText(
                        event.getCategoryName(), categoryScore))
                .actionableAdvice(RecommendationEngine.generateActionableAdvice(
                        winner.getKey(), winner.getValue()))
                .build());

        publishGenerated(saved);

        log.info("Generated recommendation id={} for userId={} attemptId={} topCareer={} match={}",
                saved.getId(), saved.getUserId(), saved.getAssessmentAttemptId(),
                saved.getTopCareerName(), saved.getOverallMatchPercentage());

        return toResponse(saved, rankings, reason);
    }

    /**
     * Scores every career in the local catalogue and orders them best-first.
     *
     * Deliberately the same shape as assessment-service's ScoringEngine.getTopCareers -- score
     * descending over a catalogue-ordered LinkedHashMap -- with one addition: on a tie, the career
     * the event already named as topCareerPath wins. That matters because today every playable
     * category damps all seven careers to the same score, so the tie-break decides rank 1 outright,
     * and disagreeing with the result screen the student just saw would be a visible contradiction.
     *
     * Score first, pin second: pinning first could seat a lower-scoring career at rank 1 if the two
     * catalogues ever drift, producing a response whose rank 1 shows a smaller percentage than
     * rank 2. As a pure tie-break it is a no-op once scores separate, and an unrecognised
     * topCareerPath (or a null one, when assessment-service has no career paths seeded) simply
     * matches nothing and leaves catalogue order in charge.
     */
    private List<Map.Entry<String, Double>> rankCareers(AssessmentCompletedEvent event) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (CareerPathDto career : CareerCatalog.ALL) {
            scores.put(career.getName(), RecommendationEngine.calculateMatchScore(
                    event.getCategoryName(),
                    event.getCategoryScorePercentage(),
                    career.getRequiredSkills()));
        }

        Comparator<Map.Entry<String, Double>> byScore = Map.Entry.comparingByValue();
        Comparator<Map.Entry<String, Double>> ordering = byScore.reversed()
                .thenComparingInt(e -> Objects.equals(e.getKey(), event.getTopCareerPath()) ? 0 : 1);

        List<Map.Entry<String, Double>> ranked = new ArrayList<>(scores.entrySet());
        // List.sort is stable, so anything still tied keeps CareerCatalog.ALL order -- which
        // mirrors assessment-service's findAll() order and is the final arbiter.
        ranked.sort(ordering);
        return ranked;
    }

    /**
     * Flips every active row, not just one. Nothing in MySQL can enforce "at most one active row
     * per user", so if two events ever raced and both inserted, this repairs the duplicate on the
     * next assessment instead of leaving it forever.
     *
     * ponytail: last-writer-wins on concurrent events for one user; SELECT ... FOR UPDATE on the
     * active row if simultaneous submissions ever become real.
     */
    private void deactivatePrevious(Long userId) {
        List<Recommendation> active =
                recommendationRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId);
        if (active.isEmpty()) {
            return;
        }
        active.forEach(previous -> previous.setIsActive(false));
        recommendationRepository.saveAll(active);
    }

    /**
     * Fail-soft: a broker outage must not cost the student their recommendation, which is already
     * saved and readable over HTTP either way.
     *
     * ponytail: publishes before the surrounding transaction commits, so a rollback after this
     * point would leave a phantom event. Kept last in generateRecommendation to shrink that window;
     * move to @TransactionalEventListener(AFTER_COMMIT) if exactly-once delivery starts mattering.
     * Same trade-off assessment-service documents on its own publisher.
     */
    private void publishGenerated(Recommendation saved) {
        try {
            rabbitTemplate.convertAndSend(
                    RecommendationConstants.EXCHANGE_NAME,
                    RecommendationConstants.ROUTING_KEY_RECOMMENDATION_GENERATED,
                    RecommendationGeneratedEvent.builder()
                            .userId(saved.getUserId())
                            .recommendationId(saved.getId())
                            .topCareerName(saved.getTopCareerName())
                            .matchPercentage(saved.getOverallMatchPercentage())
                            .categoryName(saved.getCategoryName())
                            .generatedAt(LocalDateTime.now())
                            .build());
        } catch (Exception ex) {
            log.error("Failed to publish {} for recommendationId={}: {}",
                    RecommendationConstants.ROUTING_KEY_RECOMMENDATION_GENERATED,
                    saved.getId(), ex.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public RecommendationResponse getMyRecommendation(Long userId) {
        Recommendation active = recommendationRepository
                .findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new CustomException(
                        "No recommendation found; complete an assessment first", HttpStatus.NOT_FOUND));

        return toResponse(active);
    }

    /**
     * ponytail: two extra queries per recommendation (N+1). Fine while a student has a handful of
     * assessments; add a batched findByRecommendationIdIn if histories ever grow long.
     */
    @Override
    @Transactional(readOnly = true)
    public List<RecommendationResponse> getRecommendationHistory(Long userId) {
        return recommendationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RecommendationResponse getRecommendationById(Long userId, Long recommendationId) {
        // userId is part of the query, so another user's recommendation is never loaded at all --
        // there is no window where the wrong row sits in memory awaiting an ownership check.
        Recommendation recommendation = recommendationRepository
                .findByIdAndUserId(recommendationId, userId)
                .orElseThrow(() -> new CustomException("Recommendation not found", HttpStatus.NOT_FOUND));

        return toResponse(recommendation);
    }

    @Override
    public List<CareerPathDto> getAllCareers() {
        return CareerCatalog.ALL;
    }

    private RecommendationResponse toResponse(Recommendation recommendation) {
        return toResponse(recommendation,
                careerRankingRepository.findByRecommendationIdOrderByRankAsc(recommendation.getId()),
                recommendationReasonRepository.findByRecommendationId(recommendation.getId()).orElse(null));
    }

    /**
     * One ranked list in, two lists out. Partitioning in memory keeps rank order inside both halves
     * and costs one query instead of two.
     */
    private RecommendationResponse toResponse(Recommendation recommendation,
                                              List<CareerRanking> rankings,
                                              RecommendationReason reason) {
        List<CareerRankingDto> top = new ArrayList<>();
        List<CareerRankingDto> others = new ArrayList<>();
        for (CareerRanking ranking : rankings) {
            (Boolean.TRUE.equals(ranking.getIsTopRecommendation()) ? top : others).add(toDto(ranking));
        }

        return RecommendationResponse.builder()
                .recommendationId(recommendation.getId())
                .userId(recommendation.getUserId())
                .categoryName(recommendation.getCategoryName())
                .overallMatchPercentage(recommendation.getOverallMatchPercentage())
                .topCareerName(recommendation.getTopCareerName())
                .topRecommendations(top)
                .otherCareers(others)
                .insight(reason == null ? null : toDto(reason))
                .createdAt(recommendation.getCreatedAt())
                .isActive(recommendation.getIsActive())
                .build();
    }

    private CareerRankingDto toDto(CareerRanking ranking) {
        return CareerRankingDto.builder()
                .careerName(ranking.getCareerName())
                .matchPercentage(ranking.getMatchPercentage())
                .rank(ranking.getRank())
                .isTopRecommendation(ranking.getIsTopRecommendation())
                .reasonText(ranking.getReasonText())
                .build();
    }

    private RecommendationReasonDto toDto(RecommendationReason reason) {
        return RecommendationReasonDto.builder()
                .strengthArea(reason.getStrengthArea())
                .improvementArea(reason.getImprovementArea())
                .actionableAdvice(reason.getActionableAdvice())
                .build();
    }
}
