package com.careerbridge.roadmap.service;

import com.careerbridge.roadmap.config.RabbitMQConfig;
import com.careerbridge.roadmap.dto.MilestoneResponse;
import com.careerbridge.roadmap.dto.MilestoneTemplateResponse;
import com.careerbridge.roadmap.dto.RoadmapResponse;
import com.careerbridge.roadmap.dto.RoadmapTemplateResponse;
import com.careerbridge.roadmap.event.RoadmapUpdatedEvent;
import com.careerbridge.roadmap.exception.CustomException;
import com.careerbridge.roadmap.model.MilestoneTemplate;
import com.careerbridge.roadmap.model.RoadmapTemplate;
import com.careerbridge.roadmap.model.StudentMilestone;
import com.careerbridge.roadmap.model.StudentRoadmap;
import com.careerbridge.roadmap.repository.MilestoneTemplateRepository;
import com.careerbridge.roadmap.repository.RoadmapTemplateRepository;
import com.careerbridge.roadmap.repository.StudentMilestoneRepository;
import com.careerbridge.roadmap.repository.StudentRoadmapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class RoadmapServiceImpl implements RoadmapService {

    private static final Logger log = LoggerFactory.getLogger(RoadmapServiceImpl.class);

    /** Matches auth-service's Role enum. Strings, because that is what arrives in X-User-Role. */
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ORG_ADMIN = "ORG_ADMIN";

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final RoadmapTemplateRepository roadmapTemplateRepository;
    private final MilestoneTemplateRepository milestoneTemplateRepository;
    private final StudentRoadmapRepository studentRoadmapRepository;
    private final StudentMilestoneRepository studentMilestoneRepository;
    private final RabbitTemplate rabbitTemplate;

    public RoadmapServiceImpl(RoadmapTemplateRepository roadmapTemplateRepository,
                              MilestoneTemplateRepository milestoneTemplateRepository,
                              StudentRoadmapRepository studentRoadmapRepository,
                              StudentMilestoneRepository studentMilestoneRepository,
                              RabbitTemplate rabbitTemplate) {
        this.roadmapTemplateRepository = roadmapTemplateRepository;
        this.milestoneTemplateRepository = milestoneTemplateRepository;
        this.studentRoadmapRepository = studentRoadmapRepository;
        this.studentMilestoneRepository = studentMilestoneRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Build-or-return: idempotent so a double-click, or the student clicking "Build my roadmap" for
     * a career they already built, returns the same roadmap rather than erroring or duplicating.
     * There's no separate "already exists" error response -- the fast path below and the unique
     * constraint on (student_id, career_name) together make this safe under a race, and the caller
     * never has to distinguish "just built" from "already had one".
     */
    @Override
    @Transactional
    public RoadmapResponse buildRoadmap(Long studentId, String careerName) {
        Optional<StudentRoadmap> existing =
                studentRoadmapRepository.findByStudentIdAndCareerNameIgnoreCase(studentId, careerName);
        if (existing.isPresent()) {
            // Re-clicking "Build my roadmap" for a career the student already has means "show me
            // that one now" -- so it reactivates, same as a fresh build, rather than silently no-op
            // leaving whichever OTHER roadmap was activated most recently still in front.
            StudentRoadmap roadmap = existing.get();
            roadmap.setActivatedAt(LocalDateTime.now());
            return toResponse(studentRoadmapRepository.save(roadmap));
        }

        RoadmapTemplate template = roadmapTemplateRepository
                .findByCareerNameIgnoreCaseAndIsActiveTrue(careerName)
                .orElseThrow(() -> new CustomException(
                        "No roadmap template for career '" + careerName + "'", HttpStatus.NOT_FOUND));

        List<MilestoneTemplate> steps =
                milestoneTemplateRepository.findByRoadmapTemplateIdOrderByOrderIndexAsc(template.getId());

        StudentRoadmap roadmap = StudentRoadmap.builder()
                .studentId(studentId)
                // From the template, not the caller's spelling of careerName, so the roadmap keeps
                // the canonical name even if the request came in with different casing.
                .careerName(template.getCareerName())
                .status(STATUS_IN_PROGRESS)
                // Counted from the rows actually copied, not template.getTotalMilestones(), so the
                // denominator of completionPercentage can never disagree with the milestone list.
                .totalMilestones(steps.size())
                .activatedAt(LocalDateTime.now())
                .build();

        for (MilestoneTemplate step : steps) {
            roadmap.getMilestones().add(StudentMilestone.builder()
                    .studentId(studentId)
                    .title(step.getTitle())
                    .description(step.getDescription())
                    .orderIndex(step.getOrderIndex())
                    .estimatedDays(step.getEstimatedDays())
                    .resourceUrl(step.getResourceUrl())
                    // StudentMilestone owns the foreign key; without this the cascade inserts a
                    // null student_roadmap_id against a NOT NULL column.
                    .studentRoadmap(roadmap)
                    .build());
        }

        StudentRoadmap saved = studentRoadmapRepository.save(roadmap);

        log.info("Built roadmap id={} for studentId={} career='{}' with {} milestones",
                saved.getId(), studentId, saved.getCareerName(), steps.size());

        return toResponse(saved);
    }

    /**
     * The student's current roadmap: the newest one, whatever its status.
     *
     * NOT filtered to IN_PROGRESS. Filtering was the original design and it 404s the moment a
     * student ticks off their last milestone -- the roadmap flips to COMPLETED and disappears from
     * the only endpoint that shows it, exactly when they want to see 100%. Caught in live
     * verification, not by any unit test, because a mocked repository returns whatever it is told.
     *
     * A List, not an Optional: a student who takes a second assessment holds a second roadmap, so
     * this legitimately matches several rows and a single-result finder would throw. DESC ordering
     * makes the first the newest, so a fresh recommendation still supersedes an older completed one
     * in the response.
     */
    @Override
    @Transactional(readOnly = true)
    public RoadmapResponse getMyRoadmap(Long studentId) {
        List<StudentRoadmap> roadmaps =
                studentRoadmapRepository.findByStudentIdOrderByActivatedThenStarted(studentId);

        if (roadmaps.isEmpty()) {
            throw new CustomException("No roadmap found", HttpStatus.NOT_FOUND);
        }

        return toResponse(roadmaps.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoadmapResponse> getMyRoadmaps(Long studentId) {
        return studentRoadmapRepository.findByStudentIdOrderByActivatedThenStarted(studentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public RoadmapResponse activateRoadmap(Long studentId, Long roadmapId) {
        StudentRoadmap roadmap = studentRoadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new CustomException("Roadmap not found", HttpStatus.NOT_FOUND));

        if (!Objects.equals(roadmap.getStudentId(), studentId)) {
            throw new CustomException("This roadmap does not belong to you", HttpStatus.FORBIDDEN);
        }

        roadmap.setActivatedAt(LocalDateTime.now());
        return toResponse(studentRoadmapRepository.save(roadmap));
    }

    @Override
    @Transactional
    public RoadmapResponse completeMilestone(Long studentId, Long milestoneId) {
        // Loaded by id, then checked for ownership. findByIdAndStudentId would collapse "belongs to
        // another student" and "does not exist" into the same empty Optional, and this endpoint owes
        // the caller 403 for the first and 404 for the second.
        StudentMilestone milestone = studentMilestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new CustomException("Milestone not found", HttpStatus.NOT_FOUND));

        // Objects.equals, not ==: both sides are Long objects well outside the Integer cache, so
        // reference comparison would be false for exactly the ids this is meant to allow.
        if (!Objects.equals(milestone.getStudentId(), studentId)) {
            throw new CustomException("This milestone does not belong to you", HttpStatus.FORBIDDEN);
        }

        if (Boolean.TRUE.equals(milestone.getIsCompleted())) {
            throw new CustomException("Milestone is already completed", HttpStatus.BAD_REQUEST);
        }

        milestone.setIsCompleted(true);
        milestone.setCompletedAt(LocalDateTime.now());
        studentMilestoneRepository.save(milestone);

        StudentRoadmap roadmap = milestone.getStudentRoadmap();

        // Recounted from the rows, never incremented in memory, so the stored counter cannot drift
        // from what the milestone table actually says. Must run after the save above.
        long completed = studentMilestoneRepository.countByStudentRoadmapIdAndIsCompletedTrue(roadmap.getId());
        int total = roadmap.getTotalMilestones() == null ? 0 : roadmap.getTotalMilestones();

        roadmap.setCompletedMilestones((int) completed);
        roadmap.setCompletionPercentage(percentage(completed, total));

        if (total > 0 && completed >= total) {
            roadmap.setStatus(STATUS_COMPLETED);
            roadmap.setCompletedAt(LocalDateTime.now());
        }

        StudentRoadmap saved = studentRoadmapRepository.save(roadmap);

        publishUpdated(saved);

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoadmapTemplateResponse> getAllTemplates(String callerRole) {
        requireAdmin(callerRole);

        return roadmapTemplateRepository.findByIsActiveTrueOrderByCareerNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    // ---------------------------------------------------------------------------------------------
    // Authorization
    // ---------------------------------------------------------------------------------------------

    /**
     * The template catalogue is an admin view of seed data, not student-facing content, so both
     * admin roles pass and every other role is refused. There is no per-tenant narrowing here:
     * templates are global, not owned by an organization, so X-User-Org-Id is irrelevant.
     */
    private void requireAdmin(String callerRole) {
        if (!ROLE_SUPER_ADMIN.equals(callerRole) && !ROLE_ORG_ADMIN.equals(callerRole)) {
            throw new CustomException("Only SUPER_ADMIN or ORG_ADMIN may view roadmap templates",
                    HttpStatus.FORBIDDEN);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Events and mapping
    // ---------------------------------------------------------------------------------------------

    /**
     * Fail-soft. Both rows are already committed by the time this runs, so rethrowing would report
     * failure for work that actually happened -- and the retry would then hit the
     * "already completed" 400. Nothing consumes roadmap.updated yet; a broker outage here costs a
     * future placement score refresh, not the student's progress.
     */
    private void publishUpdated(StudentRoadmap roadmap) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.ROADMAP_UPDATED_ROUTING_KEY,
                    RoadmapUpdatedEvent.builder()
                            .studentId(roadmap.getStudentId())
                            .roadmapId(roadmap.getId())
                            .completionPercentage(roadmap.getCompletionPercentage())
                            .status(roadmap.getStatus())
                            .updatedAt(LocalDateTime.now())
                            .build());
        } catch (Exception ex) {
            log.error("Failed to publish {} for roadmapId={}",
                    RabbitMQConfig.ROADMAP_UPDATED_ROUTING_KEY, roadmap.getId(), ex);
        }
    }

    /** Rounded to 2 decimals: 1 of 7 milestones is 14.285714285714286 raw, which no dashboard wants. */
    private double percentage(long completed, int total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round(completed * 100.0 / total * 100.0) / 100.0;
    }

    /**
     * Milestones are loaded through the ordered repository query rather than read off the entity's
     * LAZY collection: that collection carries no @OrderBy, so it would come back in whatever order
     * the database chose, and the dashboard renders this list top to bottom without sorting.
     */
    private RoadmapResponse toResponse(StudentRoadmap roadmap) {
        List<MilestoneResponse> milestones =
                studentMilestoneRepository.findByStudentRoadmapIdOrderByOrderIndexAsc(roadmap.getId()).stream()
                        .map(this::toResponse)
                        .toList();

        return RoadmapResponse.builder()
                .id(roadmap.getId())
                .studentId(roadmap.getStudentId())
                .careerName(roadmap.getCareerName())
                .status(roadmap.getStatus())
                .totalMilestones(roadmap.getTotalMilestones())
                .completedMilestones(roadmap.getCompletedMilestones())
                .completionPercentage(roadmap.getCompletionPercentage())
                .startedAt(roadmap.getStartedAt())
                .completedAt(roadmap.getCompletedAt())
                .milestones(milestones)
                .build();
    }

    private MilestoneResponse toResponse(StudentMilestone milestone) {
        return MilestoneResponse.builder()
                .id(milestone.getId())
                .title(milestone.getTitle())
                .description(milestone.getDescription())
                .orderIndex(milestone.getOrderIndex())
                .estimatedDays(milestone.getEstimatedDays())
                .resourceUrl(milestone.getResourceUrl())
                .isCompleted(milestone.getIsCompleted())
                .completedAt(milestone.getCompletedAt())
                .build();
    }

    private RoadmapTemplateResponse toResponse(RoadmapTemplate template) {
        List<MilestoneTemplateResponse> steps =
                milestoneTemplateRepository.findByRoadmapTemplateIdOrderByOrderIndexAsc(template.getId()).stream()
                        .map(this::toResponse)
                        .toList();

        return RoadmapTemplateResponse.builder()
                .id(template.getId())
                .careerName(template.getCareerName())
                .templateTitle(template.getTemplateTitle())
                .description(template.getDescription())
                .totalMilestones(template.getTotalMilestones())
                .estimatedWeeks(template.getEstimatedWeeks())
                .milestoneTemplates(steps)
                .build();
    }

    private MilestoneTemplateResponse toResponse(MilestoneTemplate step) {
        return MilestoneTemplateResponse.builder()
                .id(step.getId())
                .title(step.getTitle())
                .description(step.getDescription())
                .orderIndex(step.getOrderIndex())
                .estimatedDays(step.getEstimatedDays())
                .resourceUrl(step.getResourceUrl())
                .build();
    }
}
