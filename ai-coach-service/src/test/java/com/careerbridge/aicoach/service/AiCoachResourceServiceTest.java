package com.careerbridge.aicoach.service;

import com.careerbridge.aicoach.dto.MilestoneResourcesResponse;
import com.careerbridge.aicoach.dto.client.RoadmapResponseDto;
import com.careerbridge.aicoach.dto.client.RoadmapTemplateResponseDto;
import com.careerbridge.aicoach.exception.CustomException;
import com.careerbridge.aicoach.model.MilestoneResourceDocument;
import com.careerbridge.aicoach.model.ResourceItem;
import com.careerbridge.aicoach.repository.MilestoneResourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCoachResourceServiceTest {

    @Mock
    private RoadmapServiceClient roadmapServiceClient;

    @Mock
    private MilestoneResourceRepository milestoneResourceRepository;

    @Mock
    private CatalogRefresher catalogRefresher;

    private AiCoachResourceServiceImpl service() {
        return new AiCoachResourceServiceImpl(roadmapServiceClient, milestoneResourceRepository, catalogRefresher);
    }

    private static RoadmapResponseDto.MilestoneDto milestone(String title, int orderIndex) {
        return RoadmapResponseDto.MilestoneDto.builder().title(title).orderIndex(orderIndex).build();
    }

    @Test
    void getMyResources_RoadmapWithMilestones_ReturnsInOrderIndexOrder() {
        RoadmapResponseDto roadmap = RoadmapResponseDto.builder()
                .studentId(1L)
                .careerName("Backend Developer")
                .milestones(List.of(milestone("Master Core Java and OOP", 1), milestone("Databases", 2)))
                .build();
        when(roadmapServiceClient.fetchMyRoadmap(1L)).thenReturn(roadmap);
        when(milestoneResourceRepository.findByCareerName("Backend Developer")).thenReturn(List.of(
                MilestoneResourceDocument.builder()
                        .careerName("Backend Developer")
                        .milestoneTitle("Master Core Java and OOP")
                        .resources(List.of(ResourceItem.builder().title("x").url("y").type("article").platform("z").build()))
                        .build()));

        List<MilestoneResourcesResponse> result = service().getMyResources("STUDENT", 1L);

        assertEquals(2, result.size());
        assertEquals("Master Core Java and OOP", result.get(0).getMilestoneTitle());
        assertEquals("Databases", result.get(1).getMilestoneTitle());
        assertEquals(1, result.get(0).getResources().size());
    }

    @Test
    void getMyResources_NoRoadmap_ReturnsEmptyListNot404() {
        when(roadmapServiceClient.fetchMyRoadmap(1L)).thenReturn(null);

        List<MilestoneResourcesResponse> result = service().getMyResources("STUDENT", 1L);

        assertTrue(result.isEmpty());
    }

    @Test
    void getMyResources_RoadmapServiceDown_Throws503() {
        when(roadmapServiceClient.fetchMyRoadmap(1L))
                .thenThrow(new CustomException("Roadmap service is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE));

        CustomException ex = assertThrows(CustomException.class, () -> service().getMyResources("STUDENT", 1L));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void getMyResources_CatalogEmptyForCareer_ReturnsMilestonesWithEmptyResourceLists() {
        RoadmapResponseDto roadmap = RoadmapResponseDto.builder()
                .careerName("Data Scientist")
                .milestones(List.of(milestone("Statistics Foundations", 1)))
                .build();
        when(roadmapServiceClient.fetchMyRoadmap(1L)).thenReturn(roadmap);
        when(milestoneResourceRepository.findByCareerName("Data Scientist")).thenReturn(List.of());

        List<MilestoneResourcesResponse> result = service().getMyResources("STUDENT", 1L);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getResources().isEmpty());
    }

    @Test
    void getMyResources_CatalogPartiallyPopulated_OnlyMissingTitlesAreEmpty() {
        RoadmapResponseDto roadmap = RoadmapResponseDto.builder()
                .careerName("Frontend Developer")
                .milestones(List.of(milestone("HTML and CSS", 1), milestone("React Basics", 2)))
                .build();
        when(roadmapServiceClient.fetchMyRoadmap(1L)).thenReturn(roadmap);
        when(milestoneResourceRepository.findByCareerName("Frontend Developer")).thenReturn(List.of(
                MilestoneResourceDocument.builder()
                        .careerName("Frontend Developer")
                        .milestoneTitle("HTML and CSS")
                        .resources(List.of(ResourceItem.builder().title("a").url("b").type("article").platform("c").build()))
                        .build()));

        List<MilestoneResourcesResponse> result = service().getMyResources("STUDENT", 1L);

        assertEquals(1, result.get(0).getResources().size());
        assertTrue(result.get(1).getResources().isEmpty());
    }

    @Test
    void getMyResources_AnyMilestoneCount_QueriesCatalogExactlyOnce() {
        RoadmapResponseDto roadmap = RoadmapResponseDto.builder()
                .careerName("DevOps Engineer")
                .milestones(List.of(milestone("A", 1), milestone("B", 2), milestone("C", 3)))
                .build();
        when(roadmapServiceClient.fetchMyRoadmap(1L)).thenReturn(roadmap);
        when(milestoneResourceRepository.findByCareerName(anyString())).thenReturn(List.of());

        service().getMyResources("STUDENT", 1L);

        verify(milestoneResourceRepository, times(1)).findByCareerName(any());
    }

    @Test
    void getMyResources_NonStudentRole_Throws403() {
        CustomException ex = assertThrows(CustomException.class,
                () -> service().getMyResources("RECRUITER", 1L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void refreshCatalog_NonSuperAdmin_Throws403() {
        CustomException ex = assertThrows(CustomException.class, () -> service().refreshCatalog("STUDENT"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void refreshCatalog_AlreadyRunning_Throws409AndDoesNotDispatch() {
        when(catalogRefresher.tryStart()).thenReturn(false);

        CustomException ex = assertThrows(CustomException.class, () -> service().refreshCatalog("SUPER_ADMIN"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(roadmapServiceClient, never()).fetchAllTemplates(anyString());
        verify(catalogRefresher, never()).run(any());
    }

    @Test
    void refreshCatalog_RoadmapServiceDown_Throws503AndResetsFlag() {
        when(catalogRefresher.tryStart()).thenReturn(true);
        when(roadmapServiceClient.fetchAllTemplates("SUPER_ADMIN"))
                .thenThrow(new CustomException("Roadmap service is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE));

        CustomException ex = assertThrows(CustomException.class, () -> service().refreshCatalog("SUPER_ADMIN"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        verify(catalogRefresher, times(1)).reset();
        verify(catalogRefresher, never()).run(any());
    }

    @Test
    void refreshCatalog_SuperAdmin_DispatchesOnceWithFetchedTemplates() {
        when(catalogRefresher.tryStart()).thenReturn(true);
        List<RoadmapTemplateResponseDto> templates = List.of(
                RoadmapTemplateResponseDto.builder().careerName("Backend Developer").milestoneTemplates(List.of()).build());
        when(roadmapServiceClient.fetchAllTemplates("SUPER_ADMIN")).thenReturn(templates);

        service().refreshCatalog("SUPER_ADMIN");

        verify(catalogRefresher, times(1)).run(templates);
        verify(catalogRefresher, never()).reset();
    }
}
