package com.careerbridge.aicoach.service;

import com.careerbridge.aicoach.dto.client.RoadmapTemplateResponseDto;
import com.careerbridge.aicoach.model.MilestoneResourceDocument;
import com.careerbridge.aicoach.model.ResourceItem;
import com.careerbridge.aicoach.repository.MilestoneResourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Async is stripped when a plain object is constructed outside a Spring context, so run() here
 * executes synchronously on the calling (test) thread -- exactly the "no HTTP client mocking, test
 * the logic directly" convention this project follows everywhere else.
 */
@ExtendWith(MockitoExtension.class)
class CatalogRefresherTest {

    @Mock
    private TavilyClient tavilyClient;

    @Mock
    private YouTubeClient youTubeClient;

    @Mock
    private MilestoneResourceRepository milestoneResourceRepository;

    private CatalogRefresher refresher() {
        return new CatalogRefresher(tavilyClient, youTubeClient, milestoneResourceRepository);
    }

    private static RoadmapTemplateResponseDto template(String career, String... titles) {
        return RoadmapTemplateResponseDto.builder()
                .careerName(career)
                .milestoneTemplates(List.of(titles).stream()
                        .map(t -> RoadmapTemplateResponseDto.MilestoneTemplateDto.builder().title(t).build())
                        .toList())
                .build();
    }

    private ResourceItem article() {
        return ResourceItem.builder().title("t").url("u").type("article").platform("p").build();
    }

    @Test
    void run_TitleAlreadyStored_SkipsBothExternalCalls() {
        when(milestoneResourceRepository.existsByCareerNameAndMilestoneTitle("Backend Developer", "Core Java"))
                .thenReturn(true);

        refresher().run(List.of(template("Backend Developer", "Core Java")));

        verify(tavilyClient, never()).searchResources(anyString(), anyString());
        verify(youTubeClient, never()).searchVideo(anyString(), anyString());
        verify(milestoneResourceRepository, never()).save(any());
    }

    @Test
    void run_NoResourcesFound_DoesNotPersistPlaceholderDocument() {
        when(milestoneResourceRepository.existsByCareerNameAndMilestoneTitle(anyString(), anyString()))
                .thenReturn(false);
        when(tavilyClient.searchResources(anyString(), anyString())).thenReturn(List.of());
        when(youTubeClient.searchVideo(anyString(), anyString())).thenReturn(null);

        refresher().run(List.of(template("Backend Developer", "Core Java")));

        verify(milestoneResourceRepository, never()).save(any());
    }

    @Test
    void run_TavilyFailsYouTubeSucceeds_PersistsVideoOnly() {
        when(milestoneResourceRepository.existsByCareerNameAndMilestoneTitle(anyString(), anyString()))
                .thenReturn(false);
        when(tavilyClient.searchResources(anyString(), anyString())).thenReturn(List.of());
        ResourceItem video = ResourceItem.builder().title("v").url("u").type("video").platform("YouTube").build();
        when(youTubeClient.searchVideo(anyString(), anyString())).thenReturn(video);

        refresher().run(List.of(template("Backend Developer", "Core Java")));

        ArgumentCaptor<MilestoneResourceDocument> captor = ArgumentCaptor.forClass(MilestoneResourceDocument.class);
        verify(milestoneResourceRepository, times(1)).save(captor.capture());
        assertEquals(1, captor.getValue().getResources().size());
        assertEquals("video", captor.getValue().getResources().get(0).getType());
    }

    @Test
    void run_OneMilestoneThrows_ContinuesToNextMilestone() {
        when(milestoneResourceRepository.existsByCareerNameAndMilestoneTitle(eq("Backend Developer"), eq("Bad")))
                .thenThrow(new RuntimeException("boom"));
        when(milestoneResourceRepository.existsByCareerNameAndMilestoneTitle(eq("Backend Developer"), eq("Good")))
                .thenReturn(false);
        when(tavilyClient.searchResources(anyString(), anyString())).thenReturn(List.of(article()));

        refresher().run(List.of(template("Backend Developer", "Bad", "Good")));

        verify(milestoneResourceRepository, times(1)).save(any());
    }

    @Test
    void run_Always_ClearsRunningFlagInFinally() {
        CatalogRefresher refresher = refresher();
        assertTrue(refresher.tryStart());

        refresher.run(List.of());

        // run() clears the flag in finally regardless of outcome, so a fresh tryStart succeeds again.
        assertTrue(refresher.tryStart());
    }

    @Test
    void run_SameTitleTwoCareers_StoredSeparately() {
        when(milestoneResourceRepository.existsByCareerNameAndMilestoneTitle(anyString(), anyString()))
                .thenReturn(false);
        when(tavilyClient.searchResources(anyString(), anyString())).thenReturn(List.of(article()));

        refresher().run(List.of(
                template("Backend Developer", "Core Java"),
                template("Frontend Developer", "Core Java")));

        ArgumentCaptor<MilestoneResourceDocument> captor = ArgumentCaptor.forClass(MilestoneResourceDocument.class);
        verify(milestoneResourceRepository, times(2)).save(captor.capture());
        assertEquals(2, captor.getAllValues().stream().map(MilestoneResourceDocument::getCareerName).distinct().count());
    }
}
