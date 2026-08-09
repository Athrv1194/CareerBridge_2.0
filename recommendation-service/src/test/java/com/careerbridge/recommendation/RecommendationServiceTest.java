package com.careerbridge.recommendation;

import com.careerbridge.recommendation.constants.CareerCatalog;
import com.careerbridge.recommendation.dto.RecommendationResponse;
import com.careerbridge.recommendation.event.AssessmentCompletedEvent;
import com.careerbridge.recommendation.exception.CustomException;
import com.careerbridge.recommendation.model.CareerRanking;
import com.careerbridge.recommendation.model.Recommendation;
import com.careerbridge.recommendation.model.RecommendationReason;
import com.careerbridge.recommendation.repository.CareerRankingRepository;
import com.careerbridge.recommendation.repository.RecommendationReasonRepository;
import com.careerbridge.recommendation.repository.RecommendationRepository;
import com.careerbridge.recommendation.service.RecommendationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long ATTEMPT_ID = 7L;
    private static final Long RECOMMENDATION_ID = 100L;

    /** Neither playable category matches any career, so all seven tie -- today's real shape. */
    private static final String TIED_CATEGORY = "Programming Fundamentals";

    /** Matches Full Stack Developer and Frontend Developer at full relevance. */
    private static final String SPLITTING_CATEGORY = "Web Development";

    @Mock private RecommendationRepository recommendationRepository;
    @Mock private CareerRankingRepository careerRankingRepository;
    @Mock private RecommendationReasonRepository recommendationReasonRepository;
    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks private RecommendationServiceImpl recommendationService;

    private AssessmentCompletedEvent event;

    @BeforeEach
    void setUp() {
        event = AssessmentCompletedEvent.builder()
                .userId(USER_ID)
                .attemptId(ATTEMPT_ID)
                .categoryId(1L)
                .categoryName(SPLITTING_CATEGORY)
                .categoryScorePercentage(100.0)
                .topCareerPath("Full Stack Developer")
                .careerMatchPercentage(100.0)
                .completedAt(LocalDateTime.now())
                .build();
    }

    /** save() assigns an id the way the DB would, so rankings can reference it. */
    private void stubSaveAssigningId() {
        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(invocation -> {
            Recommendation toSave = invocation.getArgument(0);
            toSave.setId(RECOMMENDATION_ID);
            return toSave;
        });
    }

    private List<CareerRanking> captureSavedRankings() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CareerRanking>> captor = ArgumentCaptor.forClass(List.class);
        verify(careerRankingRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("generate: ranks every catalogue career and flags exactly the top three")
    void generateRecommendation_RanksAllSevenCareers_MarksTopThree() {
        when(recommendationRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of());
        stubSaveAssigningId();

        recommendationService.generateRecommendation(event);

        List<CareerRanking> saved = captureSavedRankings();
        assertEquals(CareerCatalog.ALL.size(), saved.size());
        assertEquals(7, saved.size());

        // ranks are 1..7, contiguous and in order
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), saved.stream().map(CareerRanking::getRank).toList());

        // Web Development matches Full Stack and Frontend at 100.0; the rest damp to 30.0
        assertEquals("Full Stack Developer", saved.get(0).getCareerName());
        assertEquals(100.0, saved.get(0).getMatchPercentage());
        assertEquals("Frontend Developer", saved.get(1).getCareerName());
        assertEquals(100.0, saved.get(1).getMatchPercentage());
        assertEquals(30.0, saved.get(2).getMatchPercentage());

        // exactly three flagged, and they are ranks 1-3
        assertEquals(3, saved.stream().filter(r -> Boolean.TRUE.equals(r.getIsTopRecommendation())).count());
        assertTrue(saved.stream().limit(3).allMatch(r -> Boolean.TRUE.equals(r.getIsTopRecommendation())));
        assertTrue(saved.stream().skip(3).noneMatch(r -> Boolean.TRUE.equals(r.getIsTopRecommendation())));

        // scores never increase as rank increases
        for (int i = 1; i < saved.size(); i++) {
            assertTrue(saved.get(i - 1).getMatchPercentage() >= saved.get(i).getMatchPercentage(),
                    "rank " + i + " outscored rank " + (i - 1));
        }
    }

    @Test
    @DisplayName("generate: uses the event's own allCareerScores map, not a local recompute from "
            + "categoryName -- categoryName is always \"Overall\" for the real aggregated event, "
            + "which matches no career's requiredSkills and would flatten every score if relied on")
    void generateRecommendation_UsesEventCareerScores_NotLocalRecompute() {
        event.setCategoryName("Overall");
        event.setTopCareerPath(null);
        event.setAllCareerScores(java.util.Map.of(
                "Full Stack Developer", 61.0,
                "Backend Developer", 54.0,
                "Frontend Developer", 48.0,
                "Data Scientist", 41.0,
                "DevOps Engineer", 35.0,
                "Mobile Developer", 29.0,
                "System Design Engineer", 22.0));

        when(recommendationRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of());
        stubSaveAssigningId();

        recommendationService.generateRecommendation(event);

        List<CareerRanking> saved = captureSavedRankings();
        // If this fell back to the local "Overall" keyword match every score would tie at 30.0 --
        // asserting distinct, event-sourced values pins that the map is actually being used.
        assertEquals("Full Stack Developer", saved.get(0).getCareerName());
        assertEquals(61.0, saved.get(0).getMatchPercentage());
        assertEquals("System Design Engineer", saved.get(6).getCareerName());
        assertEquals(22.0, saved.get(6).getMatchPercentage());
        assertEquals(7, saved.stream().map(CareerRanking::getMatchPercentage).distinct().count(),
                "all seven scores should be distinct, not flattened to a single tied value");
    }

    @Test
    @DisplayName("generate: when every career ties, the event's own top career takes rank 1")
    void generateRecommendation_TiedScores_PlacesEventTopCareerAtRankOne() {
        // Backend Developer is 2nd in catalogue order, so catalogue order alone would not pick it.
        event.setCategoryName(TIED_CATEGORY);
        event.setTopCareerPath("Backend Developer");

        when(recommendationRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of());
        stubSaveAssigningId();

        recommendationService.generateRecommendation(event);

        List<CareerRanking> saved = captureSavedRankings();
        assertTrue(saved.stream().allMatch(r -> r.getMatchPercentage() == 30.0), "expected a 7-way tie");
        assertEquals("Backend Developer", saved.get(0).getCareerName());
        assertEquals(1, saved.get(0).getRank());
    }

    @Test
    @DisplayName("generate: a null topCareerPath still produces a full ranking from the local catalogue")
    void generateRecommendation_NullTopCareerPath_StillRanksFromLocalCatalog() {
        // assessment-service sends null for both when its career_paths table is empty.
        event.setCategoryName(TIED_CATEGORY);
        event.setTopCareerPath(null);
        event.setCareerMatchPercentage(null);

        when(recommendationRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of());
        stubSaveAssigningId();

        RecommendationResponse response = recommendationService.generateRecommendation(event);

        List<CareerRanking> saved = captureSavedRankings();
        assertEquals(7, saved.size());
        // nothing to pin, so catalogue order decides
        assertEquals("Full Stack Developer", saved.get(0).getCareerName());
        assertEquals("Full Stack Developer", response.getTopCareerName());
        assertEquals(30.0, response.getOverallMatchPercentage());
    }

    @Test
    @DisplayName("generate: the previous active recommendation is deactivated, never deleted")
    void generateRecommendation_DeactivatesPreviousActiveRecommendation() {
        Recommendation previous = Recommendation.builder()
                .id(99L).userId(USER_ID).assessmentAttemptId(6L).categoryId(1L)
                .categoryName("Database & SQL").overallMatchPercentage(20.0)
                .topCareerName("Backend Developer").isActive(true).algorithmVersion("v1.0")
                .build();

        when(recommendationRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(previous));
        stubSaveAssigningId();

        recommendationService.generateRecommendation(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Recommendation>> captor = ArgumentCaptor.forClass(List.class);
        verify(recommendationRepository).saveAll(captor.capture());

        assertEquals(1, captor.getValue().size());
        assertFalse(captor.getValue().get(0).getIsActive(), "previous should be flipped inactive");
        assertEquals(99L, captor.getValue().get(0).getId(), "the old row is kept, not replaced");
    }

    @Test
    @DisplayName("generate: a broker failure does not cost the student their recommendation")
    void generateRecommendation_PublishFails_RecommendationStillSaved() {
        when(recommendationRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of());
        stubSaveAssigningId();
        doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        RecommendationResponse response =
                assertDoesNotThrow(() -> recommendationService.generateRecommendation(event));

        assertEquals(RECOMMENDATION_ID, response.getRecommendationId());
        verify(recommendationRepository).save(any(Recommendation.class));
        verify(careerRankingRepository).saveAll(any());
        verify(recommendationReasonRepository).save(any(RecommendationReason.class));
    }

    @Test
    @DisplayName("my: a student with no assessment yet gets a 404, not an empty body")
    void getMyRecommendation_NoActiveRecommendation_Throws404() {
        when(recommendationRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of());

        CustomException ex = assertThrows(CustomException.class,
                () -> recommendationService.getMyRecommendation(USER_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("by id: another student's recommendation is not found rather than returned")
    void getRecommendationById_BelongsToAnotherUser_Throws404() {
        // ownership is part of the query, so a foreign row simply never matches
        when(recommendationRepository.findByIdAndUserId(RECOMMENDATION_ID, USER_ID))
                .thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> recommendationService.getRecommendationById(USER_ID, RECOMMENDATION_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("history: returns every recommendation newest first, active and superseded alike")
    void getRecommendationHistory_ReturnsNewestFirst() {
        Recommendation newer = Recommendation.builder()
                .id(2L).userId(USER_ID).assessmentAttemptId(8L).categoryId(1L)
                .categoryName("Web Development").overallMatchPercentage(100.0)
                .topCareerName("Full Stack Developer").isActive(true).algorithmVersion("v1.0")
                .createdAt(LocalDateTime.now()).build();
        Recommendation older = Recommendation.builder()
                .id(1L).userId(USER_ID).assessmentAttemptId(7L).categoryId(2L)
                .categoryName("Database & SQL").overallMatchPercentage(20.0)
                .topCareerName("Backend Developer").isActive(false).algorithmVersion("v1.0")
                .createdAt(LocalDateTime.now().minusDays(1)).build();

        when(recommendationRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(newer, older));
        when(careerRankingRepository.findByRecommendationIdOrderByRankAsc(anyLong()))
                .thenReturn(List.of());
        when(recommendationReasonRepository.findByRecommendationId(anyLong()))
                .thenReturn(Optional.empty());

        List<RecommendationResponse> history = recommendationService.getRecommendationHistory(USER_ID);

        assertEquals(2, history.size());
        assertEquals(2L, history.get(0).getRecommendationId());
        assertTrue(history.get(0).getIsActive());
        assertEquals(1L, history.get(1).getRecommendationId());
        assertFalse(history.get(1).getIsActive(), "superseded recommendations are kept in history");
    }
}
