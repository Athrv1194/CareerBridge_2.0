package com.careerbridge.aicoach.service;

import com.careerbridge.aicoach.dto.client.RoadmapTemplateResponseDto;
import com.careerbridge.aicoach.model.MilestoneResourceDocument;
import com.careerbridge.aicoach.model.ResourceItem;
import com.careerbridge.aicoach.repository.MilestoneResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The @Async method here MUST live on a bean separate from its caller (AiCoachResourceServiceImpl)
 * -- Spring's @Async proxy only intercepts calls that arrive from OUTSIDE the bean; a self-invoked
 * call bypasses the proxy entirely and runs synchronously on the caller's thread (R4 in the plan).
 *
 * ponytail: the AtomicBoolean is a per-JVM guard, not a distributed lock -- fine for one container,
 * but two replicas could both pass tryStart() and both refresh concurrently. That costs wasted
 * quota, not correctness (the existsBy... skip check still prevents duplicate documents within a
 * single replica's run, and worst case across replicas is a handful of duplicate documents plus
 * some burned API calls). Upgrade path if replicas are ever added: a Mongo-backed lock document
 * with a TTL index, not a bare AtomicBoolean.
 */
@Component
public class CatalogRefresher {

    private static final Logger log = LoggerFactory.getLogger(CatalogRefresher.class);

    private static final long SLEEP_BETWEEN_MILESTONES_MS = 300;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final TavilyClient tavilyClient;
    private final YouTubeClient youTubeClient;
    private final MilestoneResourceRepository milestoneResourceRepository;

    public CatalogRefresher(TavilyClient tavilyClient,
                             YouTubeClient youTubeClient,
                             MilestoneResourceRepository milestoneResourceRepository) {
        this.tavilyClient = tavilyClient;
        this.youTubeClient = youTubeClient;
        this.milestoneResourceRepository = milestoneResourceRepository;
    }

    /** Synchronous CAS -- called from the request thread, before any @Async dispatch happens. */
    public boolean tryStart() {
        return running.compareAndSet(false, true);
    }

    /** Releases the flag without ever having dispatched -- used when the caller fails before run(). */
    public void reset() {
        running.set(false);
    }

    /**
     * Runs on a strictly serial background thread (spring.task.execution.pool.core-size: 1). Every
     * exception path clears the flag in finally, including one thrown by this method itself -- a
     * thrown exception from a void @Async method only reaches SimpleAsyncUncaughtExceptionHandler,
     * never the caller, so finally is the only place this flag can reliably be released.
     */
    @Async
    public void run(List<RoadmapTemplateResponseDto> templates) {
        try {
            if (templates == null) {
                return;
            }
            for (RoadmapTemplateResponseDto template : templates) {
                if (!processCareer(template)) {
                    return; // interrupted mid-run
                }
            }
        } finally {
            running.set(false);
        }
    }

    private boolean processCareer(RoadmapTemplateResponseDto template) {
        if (template.getMilestoneTemplates() == null) {
            return true;
        }
        for (RoadmapTemplateResponseDto.MilestoneTemplateDto milestone : template.getMilestoneTemplates()) {
            try {
                processMilestone(template.getCareerName(), milestone.getTitle());
            } catch (Exception e) {
                // Per-milestone try/catch: one bad title must not abort the remaining ones.
                log.warn("Failed to enrich milestone='{}' career='{}': {}",
                        milestone.getTitle(), template.getCareerName(), e.getMessage());
            }

            try {
                Thread.sleep(SLEEP_BETWEEN_MILESTONES_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    private void processMilestone(String careerName, String milestoneTitle) {
        if (milestoneResourceRepository.existsByCareerNameAndMilestoneTitle(careerName, milestoneTitle)) {
            return;
        }

        List<ResourceItem> resources = new ArrayList<>(tavilyClient.searchResources(milestoneTitle, careerName));
        ResourceItem video = youTubeClient.searchVideo(milestoneTitle, careerName);
        if (video != null) {
            resources.add(video);
        }

        // R2: never persist a document with an empty resources list -- existsByCareerNameAndMilestoneTitle
        // is the skip check above, so an empty placeholder would make this milestone permanently
        // un-enrichable on every future refresh.
        if (resources.isEmpty()) {
            log.warn("No resources found for milestone='{}' career='{}'; not persisting", milestoneTitle, careerName);
            return;
        }

        milestoneResourceRepository.save(MilestoneResourceDocument.builder()
                .careerName(careerName)
                .milestoneTitle(milestoneTitle)
                .resources(resources)
                .fetchedAt(LocalDateTime.now())
                .build());
    }
}
