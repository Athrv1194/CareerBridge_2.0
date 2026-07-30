package com.careerbridge.roadmap;

import com.careerbridge.roadmap.event.RecommendationGeneratedEvent;
import com.careerbridge.roadmap.exception.CustomException;
import com.careerbridge.roadmap.model.MilestoneTemplate;
import com.careerbridge.roadmap.model.RoadmapTemplate;
import com.careerbridge.roadmap.model.StudentMilestone;
import com.careerbridge.roadmap.model.StudentRoadmap;
import com.careerbridge.roadmap.repository.MilestoneTemplateRepository;
import com.careerbridge.roadmap.repository.RoadmapTemplateRepository;
import com.careerbridge.roadmap.repository.StudentMilestoneRepository;
import com.careerbridge.roadmap.repository.StudentRoadmapRepository;
import com.careerbridge.roadmap.service.RoadmapServiceImpl;
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

/** Pure Mockito -- no Spring context, no database, no broker. */
@ExtendWith(MockitoExtension.class)
class RoadmapServiceTest {

    @Mock
    private RoadmapTemplateRepository roadmapTemplateRepository;
    @Mock
    private MilestoneTemplateRepository milestoneTemplateRepository;
    @Mock
    private StudentRoadmapRepository studentRoadmapRepository;
    @Mock
    private StudentMilestoneRepository studentMilestoneRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private RoadmapServiceImpl roadmapService;

    private static RoadmapTemplate template(Long id) {
        return RoadmapTemplate.builder()
                .id(id)
                .careerName("Backend Developer")
                .templateTitle("Backend Developer Roadmap")
                .totalMilestones(2)
                .estimatedWeeks(4)
                .isActive(true)
                .build();
    }

    private static List<MilestoneTemplate> steps(RoadmapTemplate template) {
        return List.of(
                MilestoneTemplate.builder().id(101L).title("Step 1").orderIndex(1)
                        .estimatedDays(7).roadmapTemplate(template).build(),
                MilestoneTemplate.builder().id(102L).title("Step 2").orderIndex(2)
                        .estimatedDays(7).roadmapTemplate(template).build());
    }

    private static StudentRoadmap roadmap(Long id, int total, int completed) {
        return StudentRoadmap.builder()
                .id(id)
                .studentId(1L)
                .recommendationId(50L)
                .careerName("Backend Developer")
                .status("IN_PROGRESS")
                .totalMilestones(total)
                .completedMilestones(completed)
                .completionPercentage(completed * 100.0 / total)
                .build();
    }

    private static StudentMilestone milestone(Long id, Long studentId, boolean completed, StudentRoadmap roadmap) {
        return StudentMilestone.builder()
                .id(id)
                .studentId(studentId)
                .title("Step 1")
                .orderIndex(1)
                .estimatedDays(7)
                .isCompleted(completed)
                .studentRoadmap(roadmap)
                .build();
    }

    // -------------------------------------------------------------------------------------------
    // generateRoadmap
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a valid event creates a roadmap with milestones copied from the template")
    void generateRoadmap_ValidEvent_CreatesRoadmapWithMilestones() {
        RoadmapTemplate tmpl = template(10L);
        when(studentRoadmapRepository.findByStudentIdAndRecommendationId(1L, 50L)).thenReturn(Optional.empty());
        when(roadmapTemplateRepository.findByCareerNameIgnoreCaseAndIsActiveTrue("Backend Developer"))
                .thenReturn(Optional.of(tmpl));
        when(milestoneTemplateRepository.findByRoadmapTemplateIdOrderByOrderIndexAsc(10L))
                .thenReturn(steps(tmpl));
        when(studentRoadmapRepository.save(any(StudentRoadmap.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RecommendationGeneratedEvent event = RecommendationGeneratedEvent.builder()
                .userId(1L).recommendationId(50L).topCareerName("Backend Developer")
                .matchPercentage(80.0).generatedAt(LocalDateTime.now()).build();

        roadmapService.generateRoadmap(event);

        ArgumentCaptor<StudentRoadmap> captor = ArgumentCaptor.forClass(StudentRoadmap.class);
        verify(studentRoadmapRepository).save(captor.capture());
        StudentRoadmap saved = captor.getValue();
        assertEquals("Backend Developer", saved.getCareerName());
        assertEquals(2, saved.getTotalMilestones());
        assertEquals(2, saved.getMilestones().size());
        assertEquals("IN_PROGRESS", saved.getStatus());
    }

    @Test
    @DisplayName("a redelivered event for an existing roadmap is skipped, idempotently")
    void generateRoadmap_DuplicateEvent_SkipsIdempotent() {
        when(studentRoadmapRepository.findByStudentIdAndRecommendationId(1L, 50L))
                .thenReturn(Optional.of(roadmap(1L, 2, 0)));

        RecommendationGeneratedEvent event = RecommendationGeneratedEvent.builder()
                .userId(1L).recommendationId(50L).topCareerName("Backend Developer").build();

        roadmapService.generateRoadmap(event);

        verify(studentRoadmapRepository, never()).save(any());
        verify(roadmapTemplateRepository, never()).findByCareerNameIgnoreCaseAndIsActiveTrue(anyString());
    }

    @Test
    @DisplayName("no matching template logs and returns instead of throwing")
    void generateRoadmap_NoTemplateFound_LogsAndReturns() {
        when(studentRoadmapRepository.findByStudentIdAndRecommendationId(1L, 50L)).thenReturn(Optional.empty());
        when(roadmapTemplateRepository.findByCareerNameIgnoreCaseAndIsActiveTrue("Underwater Basket Weaver"))
                .thenReturn(Optional.empty());

        RecommendationGeneratedEvent event = RecommendationGeneratedEvent.builder()
                .userId(1L).recommendationId(50L).topCareerName("Underwater Basket Weaver").build();

        roadmapService.generateRoadmap(event);

        verify(studentRoadmapRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------------------------
    // getMyRoadmap
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("returns the newest IN_PROGRESS roadmap with ordered milestones")
    void getMyRoadmap_ValidStudent_ReturnsRoadmap() {
        StudentRoadmap active = roadmap(1L, 2, 0);
        when(studentRoadmapRepository.findByStudentIdAndStatusOrderByStartedAtDesc(1L, "IN_PROGRESS"))
                .thenReturn(List.of(active));
        when(studentMilestoneRepository.findByStudentRoadmapIdOrderByOrderIndexAsc(1L))
                .thenReturn(List.of(milestone(200L, 1L, false, active)));

        var response = roadmapService.getMyRoadmap(1L);

        assertEquals(1L, response.getId());
        assertEquals(1, response.getMilestones().size());
    }

    @Test
    @DisplayName("no active roadmap is a 404")
    void getMyRoadmap_NoRoadmap_Throws404() {
        when(studentRoadmapRepository.findByStudentIdAndStatusOrderByStartedAtDesc(1L, "IN_PROGRESS"))
                .thenReturn(List.of());

        CustomException ex = assertThrows(CustomException.class, () -> roadmapService.getMyRoadmap(1L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // -------------------------------------------------------------------------------------------
    // completeMilestone
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("completing a milestone recomputes the roadmap's percentage from the row count")
    void completeMilestone_ValidMilestone_UpdatesPercentage() {
        StudentRoadmap parentRoadmap = roadmap(1L, 2, 0);
        StudentMilestone target = milestone(200L, 1L, false, parentRoadmap);
        when(studentMilestoneRepository.findById(200L)).thenReturn(Optional.of(target));
        when(studentMilestoneRepository.save(any(StudentMilestone.class))).thenReturn(target);
        when(studentMilestoneRepository.countByStudentRoadmapIdAndIsCompletedTrue(1L)).thenReturn(1L);
        when(studentRoadmapRepository.save(any(StudentRoadmap.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentMilestoneRepository.findByStudentRoadmapIdOrderByOrderIndexAsc(1L))
                .thenReturn(List.of(target));

        var response = roadmapService.completeMilestone(1L, 200L);

        assertEquals(1, response.getCompletedMilestones());
        assertEquals(50.0, response.getCompletionPercentage());
        assertEquals("IN_PROGRESS", response.getStatus());
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("a milestone belonging to another student is a 403")
    void completeMilestone_WrongStudent_Throws403() {
        StudentRoadmap parentRoadmap = roadmap(1L, 2, 0);
        StudentMilestone target = milestone(200L, 1L, false, parentRoadmap);
        when(studentMilestoneRepository.findById(200L)).thenReturn(Optional.of(target));

        CustomException ex = assertThrows(CustomException.class,
                () -> roadmapService.completeMilestone(999L, 200L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(studentMilestoneRepository, never()).save(any());
    }

    @Test
    @DisplayName("completing an already-completed milestone is a 400")
    void completeMilestone_AlreadyCompleted_Throws400() {
        StudentRoadmap parentRoadmap = roadmap(1L, 2, 1);
        StudentMilestone target = milestone(200L, 1L, true, parentRoadmap);
        when(studentMilestoneRepository.findById(200L)).thenReturn(Optional.of(target));

        CustomException ex = assertThrows(CustomException.class,
                () -> roadmapService.completeMilestone(1L, 200L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(studentMilestoneRepository, never()).save(any());
    }

    @Test
    @DisplayName("completing the last milestone marks the roadmap COMPLETED")
    void completeMilestone_LastMilestone_SetsRoadmapCompleted() {
        StudentRoadmap parentRoadmap = roadmap(1L, 2, 1);
        StudentMilestone target = milestone(200L, 1L, false, parentRoadmap);
        when(studentMilestoneRepository.findById(200L)).thenReturn(Optional.of(target));
        when(studentMilestoneRepository.save(any(StudentMilestone.class))).thenReturn(target);
        when(studentMilestoneRepository.countByStudentRoadmapIdAndIsCompletedTrue(1L)).thenReturn(2L);
        when(studentRoadmapRepository.save(any(StudentRoadmap.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentMilestoneRepository.findByStudentRoadmapIdOrderByOrderIndexAsc(1L))
                .thenReturn(List.of(target));

        var response = roadmapService.completeMilestone(1L, 200L);

        assertEquals("COMPLETED", response.getStatus());
        assertEquals(100.0, response.getCompletionPercentage());
    }

    @Test
    @DisplayName("a broker outage does not fail milestone completion: the publish is fail-soft")
    void completeMilestone_BrokerDown_StillSucceeds() {
        StudentRoadmap parentRoadmap = roadmap(1L, 2, 0);
        StudentMilestone target = milestone(200L, 1L, false, parentRoadmap);
        when(studentMilestoneRepository.findById(200L)).thenReturn(Optional.of(target));
        when(studentMilestoneRepository.save(any(StudentMilestone.class))).thenReturn(target);
        when(studentMilestoneRepository.countByStudentRoadmapIdAndIsCompletedTrue(1L)).thenReturn(1L);
        when(studentRoadmapRepository.save(any(StudentRoadmap.class))).thenAnswer(inv -> inv.getArgument(0));
        when(studentMilestoneRepository.findByStudentRoadmapIdOrderByOrderIndexAsc(1L))
                .thenReturn(List.of(target));
        doThrow(new org.springframework.amqp.AmqpException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        var response = roadmapService.completeMilestone(1L, 200L);

        assertEquals(1, response.getCompletedMilestones());
        assertTrue(response.getCompletionPercentage() > 0);
    }

    @Test
    @DisplayName("a nonexistent milestone is a 404")
    void completeMilestone_NotFound_Throws404() {
        when(studentMilestoneRepository.findById(999L)).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class,
                () -> roadmapService.completeMilestone(1L, 999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}
