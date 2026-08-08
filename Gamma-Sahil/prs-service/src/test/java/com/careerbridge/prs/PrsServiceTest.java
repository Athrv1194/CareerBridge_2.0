package com.careerbridge.prs;

import com.careerbridge.prs.dto.PrsLeaderboardEntry;
import com.careerbridge.prs.dto.PrsResponse;
import com.careerbridge.prs.exception.CustomException;
import com.careerbridge.prs.model.PlacementReadinessScore;
import com.careerbridge.prs.repository.PlacementReadinessScoreRepository;
import com.careerbridge.prs.service.PrsServiceImpl;
import com.careerbridge.prs.service.StudentServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pure Mockito -- no Spring context, no database, no broker, no student-service. */
@ExtendWith(MockitoExtension.class)
class PrsServiceTest {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ORG_ADMIN = "ORG_ADMIN";
    private static final String STUDENT = "STUDENT";

    @Mock
    private PlacementReadinessScoreRepository prsRepository;
    @Mock
    private StudentServiceClient studentServiceClient;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PrsServiceImpl prsService;

    private static PlacementReadinessScore prs(Long studentId, double assessment, double roadmap,
                                               double profile, double total, String grade) {
        return PlacementReadinessScore.builder()
                .id(1L)
                .studentId(studentId)
                .organizationId(7L)
                .assessmentScore(assessment)
                .roadmapScore(roadmap)
                .profileScore(profile)
                .totalScore(total)
                .grade(grade)
                .build();
    }

    // -------------------------------------------------------------------------------------------
    // initialisePrs
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a new student gets an all-zero record with grade F and their organization")
    void initialisePrs_NewStudent_CreatesZeroRecord() {
        when(prsRepository.existsByStudentId(1L)).thenReturn(false);

        prsService.initialisePrs(1L, 7L);

        ArgumentCaptor<PlacementReadinessScore> captor =
                ArgumentCaptor.forClass(PlacementReadinessScore.class);
        verify(prsRepository).save(captor.capture());
        PlacementReadinessScore saved = captor.getValue();
        assertEquals(1L, saved.getStudentId());
        assertEquals(7L, saved.getOrganizationId());
        assertEquals(0.0, saved.getAssessmentScore());
        assertEquals(0.0, saved.getRoadmapScore());
        assertEquals(0.0, saved.getProfileScore());
        assertEquals(0.0, saved.getTotalScore());
        // @Builder.Default is what makes this "F" rather than null.
        assertEquals("F", saved.getGrade());
    }

    @Test
    @DisplayName("a redelivered registration is skipped, idempotently")
    void initialisePrs_ExistingStudent_SkipsIdempotent() {
        when(prsRepository.existsByStudentId(1L)).thenReturn(true);

        prsService.initialisePrs(1L, 7L);

        verify(prsRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------------------------
    // updateAssessmentScore
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("assessment score is stored raw and the total is recomputed from all three inputs")
    void updateAssessmentScore_ValidEvent_UpdatesAndRecomputesTotal() {
        when(prsRepository.findByStudentId(1L))
                .thenReturn(Optional.of(prs(1L, 0.0, 50.0, 0.0, 15.0, "F")));
        when(studentServiceClient.fetchProfileScore(1L)).thenReturn(80.0);
        when(prsRepository.save(any(PlacementReadinessScore.class))).thenAnswer(i -> i.getArgument(0));

        prsService.updateAssessmentScore(1L, 90.0);

        ArgumentCaptor<PlacementReadinessScore> captor =
                ArgumentCaptor.forClass(PlacementReadinessScore.class);
        verify(prsRepository).save(captor.capture());
        PlacementReadinessScore saved = captor.getValue();
        assertEquals(90.0, saved.getAssessmentScore());
        assertEquals(80.0, saved.getProfileScore());
        // 90*0.40 + 50*0.30 + 80*0.20 = 36 + 15 + 16 = 67.0
        assertEquals(67.0, saved.getTotalScore());
        assertEquals("B", saved.getGrade());
    }

    @Test
    @DisplayName("a failed profile fetch keeps the last known profileScore, it is never overwritten with -1")
    void updateAssessmentScore_ProfileCallFails_UsesExistingProfileScore() {
        when(prsRepository.findByStudentId(1L))
                .thenReturn(Optional.of(prs(1L, 0.0, 0.0, 60.0, 12.0, "F")));
        when(studentServiceClient.fetchProfileScore(1L))
                .thenReturn(StudentServiceClient.PROFILE_FETCH_FAILED);
        when(prsRepository.save(any(PlacementReadinessScore.class))).thenAnswer(i -> i.getArgument(0));

        prsService.updateAssessmentScore(1L, 50.0);

        ArgumentCaptor<PlacementReadinessScore> captor =
                ArgumentCaptor.forClass(PlacementReadinessScore.class);
        verify(prsRepository).save(captor.capture());
        PlacementReadinessScore saved = captor.getValue();
        // The sentinel must never reach the entity: it is outside the legal 0-100 range and would
        // drag the total negative.
        assertEquals(60.0, saved.getProfileScore());
        // 50*0.40 + 0 + 60*0.20 = 20 + 12 = 32.0 -- still computed, the event is not dropped.
        assertEquals(32.0, saved.getTotalScore());
        // 32.0 falls in the 20-39 band, so D. Recomputed from the arithmetic above rather than
        // carried over from a sibling test: grades here are input-dependent and a plausible-looking
        // wrong letter is exactly how assessment-service's SEV-4 got written.
        assertEquals("D", saved.getGrade());
    }

    @Test
    @DisplayName("an event for a student with no record creates one defensively rather than dropping it")
    void updateAssessmentScore_NoExistingRecord_CreatesDefensively() {
        when(prsRepository.findByStudentId(99L)).thenReturn(Optional.empty());
        when(studentServiceClient.fetchProfileScore(99L)).thenReturn(0.0);
        when(prsRepository.save(any(PlacementReadinessScore.class))).thenAnswer(i -> i.getArgument(0));

        prsService.updateAssessmentScore(99L, 40.0);

        ArgumentCaptor<PlacementReadinessScore> captor =
                ArgumentCaptor.forClass(PlacementReadinessScore.class);
        verify(prsRepository).save(captor.capture());
        PlacementReadinessScore saved = captor.getValue();
        assertEquals(99L, saved.getStudentId());
        assertEquals(40.0, saved.getAssessmentScore());
        // Defensively created rows carry no organization -- only student.registered supplies one.
        assertEquals(null, saved.getOrganizationId());
    }

    // -------------------------------------------------------------------------------------------
    // updateRoadmapScore
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("roadmap score is stored raw and the total is recomputed")
    void updateRoadmapScore_ValidEvent_UpdatesAndRecomputesTotal() {
        when(prsRepository.findByStudentId(1L))
                .thenReturn(Optional.of(prs(1L, 100.0, 0.0, 0.0, 40.0, "C")));
        when(studentServiceClient.fetchProfileScore(1L))
                .thenReturn(StudentServiceClient.PROFILE_FETCH_FAILED);
        when(prsRepository.save(any(PlacementReadinessScore.class))).thenAnswer(i -> i.getArgument(0));

        prsService.updateRoadmapScore(1L, 100.0);

        ArgumentCaptor<PlacementReadinessScore> captor =
                ArgumentCaptor.forClass(PlacementReadinessScore.class);
        verify(prsRepository).save(captor.capture());
        // 100*0.40 + 100*0.30 + 0 = 70.0
        assertEquals(70.0, captor.getValue().getTotalScore());
        assertEquals("B", captor.getValue().getGrade());
    }

    @Test
    @DisplayName("a successful profile refresh feeds the new value straight into the total")
    void updateRoadmapScore_ProfileRefreshSucceeds_TotalUsesNewProfile() {
        when(prsRepository.findByStudentId(1L))
                .thenReturn(Optional.of(prs(1L, 100.0, 0.0, 10.0, 42.0, "C")));
        when(studentServiceClient.fetchProfileScore(1L)).thenReturn(100.0);
        when(prsRepository.save(any(PlacementReadinessScore.class))).thenAnswer(i -> i.getArgument(0));

        prsService.updateRoadmapScore(1L, 100.0);

        ArgumentCaptor<PlacementReadinessScore> captor =
                ArgumentCaptor.forClass(PlacementReadinessScore.class);
        verify(prsRepository).save(captor.capture());
        assertEquals(100.0, captor.getValue().getProfileScore());
        // 100*0.40 + 100*0.30 + 100*0.20 + 0*0.10 = 90.0 -- resumeScore defaults to 0.0 on the
        // fixture built by prs(...), which does not set it explicitly.
        assertEquals(90.0, captor.getValue().getTotalScore());
        assertEquals("A", captor.getValue().getGrade());
    }

    @Test
    @DisplayName("resume score is stored raw and the total now reaches 100 when all four inputs are maxed")
    void updateResumeScore_ValidEvent_UpdatesAndRecomputesTotal() {
        when(prsRepository.findByStudentId(1L))
                .thenReturn(Optional.of(prs(1L, 100.0, 100.0, 100.0, 90.0, "A")));
        when(studentServiceClient.fetchProfileScore(1L)).thenReturn(100.0);
        when(prsRepository.save(any(PlacementReadinessScore.class))).thenAnswer(i -> i.getArgument(0));

        prsService.updateResumeScore(1L, 100.0);

        ArgumentCaptor<PlacementReadinessScore> captor =
                ArgumentCaptor.forClass(PlacementReadinessScore.class);
        verify(prsRepository).save(captor.capture());
        PlacementReadinessScore saved = captor.getValue();
        assertEquals(100.0, saved.getResumeScore());
        // 100*0.40 + 100*0.30 + 100*0.20 + 100*0.10 = 100.0 -- the slot is live, not reserved.
        assertEquals(100.0, saved.getTotalScore());
        assertEquals("A", saved.getGrade());
    }

    @Test
    @DisplayName("a student who never generated a resume keeps resumeScore at 0 and totalScore unaffected")
    void updateAssessmentScore_NoResumeYet_ResumeScoreStaysZero() {
        PlacementReadinessScore record = prs(1L, 0.0, 50.0, 0.0, 15.0, "F");
        record.setResumeScore(0.0);
        when(prsRepository.findByStudentId(1L)).thenReturn(Optional.of(record));
        when(studentServiceClient.fetchProfileScore(1L)).thenReturn(80.0);
        when(prsRepository.save(any(PlacementReadinessScore.class))).thenAnswer(i -> i.getArgument(0));

        prsService.updateAssessmentScore(1L, 90.0);

        ArgumentCaptor<PlacementReadinessScore> captor =
                ArgumentCaptor.forClass(PlacementReadinessScore.class);
        verify(prsRepository).save(captor.capture());
        // Same 67.0 as updateAssessmentScore_ValidEvent_UpdatesAndRecomputesTotal -- activating the
        // resume slot must not change a score for a student who has not generated one.
        assertEquals(67.0, captor.getValue().getTotalScore());
    }

    // -------------------------------------------------------------------------------------------
    // Grade boundaries
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("every grade boundary is correct on both sides")
    void computeGrade_BoundaryValues_CorrectGrade() {
        // computeGrade is private, so it is exercised through the public path: assessmentScore is
        // driven to a value whose 0.40 weighting lands exactly on each boundary, with the other two
        // inputs held at zero.
        assertEquals("A", gradeForTotal(80.0));
        assertEquals("B", gradeForTotal(79.9));
        assertEquals("B", gradeForTotal(60.0));
        assertEquals("C", gradeForTotal(59.9));
        assertEquals("C", gradeForTotal(40.0));
        assertEquals("D", gradeForTotal(39.9));
        assertEquals("D", gradeForTotal(20.0));
        assertEquals("F", gradeForTotal(19.9));
        assertEquals("F", gradeForTotal(0.0));
    }

    /** Drives a target totalScore through the real code path and returns the grade it produced. */
    private String gradeForTotal(double targetTotal) {
        // assessmentScore x 0.40 == targetTotal, so assessmentScore == targetTotal / 0.40.
        double assessment = targetTotal / 0.40;

        when(prsRepository.findByStudentId(5L))
                .thenReturn(Optional.of(prs(5L, 0.0, 0.0, 0.0, 0.0, "F")));
        when(studentServiceClient.fetchProfileScore(5L))
                .thenReturn(StudentServiceClient.PROFILE_FETCH_FAILED);
        when(prsRepository.save(any(PlacementReadinessScore.class))).thenAnswer(i -> i.getArgument(0));

        prsService.updateAssessmentScore(5L, assessment);

        ArgumentCaptor<PlacementReadinessScore> captor =
                ArgumentCaptor.forClass(PlacementReadinessScore.class);
        verify(prsRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue().getGrade();
    }

    // -------------------------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("getMyPrs returns the score with a breakdown whose contributions sum to the total")
    void getMyPrs_ValidStudent_ReturnsPrsWithBreakdown() {
        when(prsRepository.findByStudentId(1L))
                .thenReturn(Optional.of(prs(1L, 90.0, 50.0, 80.0, 67.0, "B")));

        PrsResponse response = prsService.getMyPrs(1L);

        assertEquals(67.0, response.getTotalScore());
        assertEquals("B", response.getGrade());
        assertEquals(40, response.getBreakdown().getAssessmentWeight());
        assertEquals(36.0, response.getBreakdown().getAssessmentContribution());
        assertEquals(15.0, response.getBreakdown().getRoadmapContribution());
        assertEquals(16.0, response.getBreakdown().getProfileContribution());
        assertEquals(10, response.getBreakdown().getResumeWeight());
        // prs(...) does not set resumeScore, so it defaults to 0.0 -- same fixture, live slot.
        assertEquals(0.0, response.getBreakdown().getResumeContribution());
        // The breakdown must actually add up -- this is what the endpoint promises.
        double sum = response.getBreakdown().getAssessmentContribution()
                + response.getBreakdown().getRoadmapContribution()
                + response.getBreakdown().getProfileContribution()
                + response.getBreakdown().getResumeContribution();
        assertEquals(response.getTotalScore(), sum, 0.001);
    }

    @Test
    @DisplayName("getMyPrs is a 404 when the student has no record")
    void getMyPrs_NoRecord_Throws404() {
        when(prsRepository.findByStudentId(1L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> prsService.getMyPrs(1L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("SUPER_ADMIN may read another student's score")
    void getPrsByStudentId_SuperAdmin_Returns200() {
        when(prsRepository.findByStudentId(2L))
                .thenReturn(Optional.of(prs(2L, 20.0, 20.0, 20.0, 18.0, "F")));

        PrsResponse response = prsService.getPrsByStudentId(1L, SUPER_ADMIN, 2L);

        assertEquals(2L, response.getStudentId());
    }

    @Test
    @DisplayName("a STUDENT may not read another student's score, and the row is never loaded")
    void getPrsByStudentId_Student_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> prsService.getPrsByStudentId(1L, STUDENT, 2L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        // 403 before the lookup: probing ids must not reveal which students exist.
        verify(prsRepository, never()).findByStudentId(anyLong());
    }

    // -------------------------------------------------------------------------------------------
    // Leaderboard
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("ORG_ADMIN sees a leaderboard scoped to their own organization, ranked from 1")
    void getLeaderboard_OrgAdmin_ScopedToOwnOrg() {
        when(prsRepository.findByOrganizationIdOrderByTotalScoreDesc(7L))
                .thenReturn(List.of(prs(1L, 0, 0, 0, 80.0, "A"), prs(2L, 0, 0, 0, 50.0, "C")));

        List<PrsLeaderboardEntry> board = prsService.getLeaderboard(ORG_ADMIN, 7L);

        assertEquals(2, board.size());
        assertEquals(1, board.get(0).getRank());
        assertEquals(2, board.get(1).getRank());
        assertEquals(80.0, board.get(0).getTotalScore());
        // The global finder must not be touched -- that is the cross-tenant leak this prevents.
        verify(prsRepository, never()).findAllByOrderByTotalScoreDesc();
    }

    @Test
    @DisplayName("SUPER_ADMIN sees every organization")
    void getLeaderboard_SuperAdmin_ReturnsAllOrgs() {
        when(prsRepository.findAllByOrderByTotalScoreDesc())
                .thenReturn(List.of(prs(1L, 0, 0, 0, 90.0, "A")));

        List<PrsLeaderboardEntry> board = prsService.getLeaderboard(SUPER_ADMIN, null);

        assertEquals(1, board.size());
        assertEquals(1, board.get(0).getRank());
        verify(prsRepository, never()).findByOrganizationIdOrderByTotalScoreDesc(anyLong());
    }

    @Test
    @DisplayName("an ORG_ADMIN with no organization header gets an empty board, never everyone's")
    void getLeaderboard_OrgAdminWithNoOrg_ReturnsEmptyNotGlobal() {
        List<PrsLeaderboardEntry> board = prsService.getLeaderboard(ORG_ADMIN, null);

        assertTrue(board.isEmpty());
        // Failing closed: treating a missing org as "show everything" would be a cross-tenant leak
        // triggered by an absent header.
        verify(prsRepository, never()).findAllByOrderByTotalScoreDesc();
    }

    @Test
    @DisplayName("a STUDENT may not read the leaderboard")
    void getLeaderboard_Student_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> prsService.getLeaderboard(STUDENT, 7L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // -------------------------------------------------------------------------------------------
    // Fail-soft publish
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a broker outage does not fail the score update: the publish is fail-soft")
    void publishPrsUpdated_BrokerDown_StillSucceeds() {
        when(prsRepository.findByStudentId(1L))
                .thenReturn(Optional.of(prs(1L, 0.0, 0.0, 0.0, 0.0, "F")));
        when(studentServiceClient.fetchProfileScore(1L)).thenReturn(50.0);
        when(prsRepository.save(any(PlacementReadinessScore.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new org.springframework.amqp.AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        // The row is committed before the publish runs, so rethrowing would report a failure for
        // work that actually happened.
        prsService.updateAssessmentScore(1L, 100.0);

        verify(prsRepository).save(any(PlacementReadinessScore.class));
    }
}
