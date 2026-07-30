package com.careerbridge.roadmap.service;

import com.careerbridge.roadmap.config.RabbitMQConfig;
import com.careerbridge.roadmap.dto.MilestoneResponse;
import com.careerbridge.roadmap.dto.MilestoneTemplateResponse;
import com.careerbridge.roadmap.dto.RoadmapResponse;
import com.careerbridge.roadmap.dto.RoadmapTemplateResponse;
import com.careerbridge.roadmap.event.RecommendationGeneratedEvent;
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
     * Called only from RoadmapEventConsumer. @Transactional is safe alongside that listener's
     * fail-soft catch because the transaction opens and closes inside this proxied call -- by the
     * time an exception reaches the consumer it has already rolled back, so the catch is not
     * swallowing a still-open rollback-only transaction.
     */
    @Override
    @Transactional
    public void generateRoadmap(RecommendationGeneratedEvent event) {
        if (event == null || event.getUserId() == null || event.getRecommendationId() == null) {
            log.warn("Ignoring {} with no userId or recommendationId",
                    RabbitMQConfig.RECOMMENDATION_GENERATED_ROUTING_KEY);
            return;
        }

        Long studentId = event.getUserId();
        Long recommendationId = event.getRecommendationId();

        // Fast path only. The unique constraint on (student_id, recommendation_id) is the real
        // guarantee: RabbitMQ is at-least-once, so a redelivery can race this check, and the loser
        // surfaces as a DataIntegrityViolationException the consumer discards.
        if (studentRoadmapRepository.findByStudentIdAndRecommendationId(studentId, recommendationId).isPresent()) {
            log.info("Roadmap already exists for studentId={} recommendationId={}, skipping",
                    studentId, recommendationId);
            return;
        }

        // Fail-soft, not an exception: a career with no seeded template must not take the listener
        // down for every other student. The most likely cause is a career renamed in
        // recommendation-service's CareerCatalog without the matching edit to RoadmapDataSeeder,
        // which this WARN is the only signal of.
        RoadmapTemplate template = roadmapTemplateRepository
                .findByCareerNameIgnoreCaseAndIsActiveTrue(event.getTopCareerName())
                .orElse(null);
        if (template == null) {
            log.warn("No active roadmap template for career '{}' (studentId={}); no roadmap generated",
                    event.getTopCareerName(), studentId);
            return;
        }

        List<MilestoneTemplate> steps =
                milestoneTemplateRepository.findByRoadmapTemplateIdOrderByOrderIndexAsc(template.getId());

        StudentRoadmap roadmap = StudentRoadmap.builder()
                .studentId(studentId)
                .recommendationId(recommendationId)
                // From the template, not the event: the event's name is what matched, but the
                // template's is the canonical spelling.
                .careerName(template.getCareerName())
                .status(STATUS_IN_PROGRESS)
                // Counted from the rows actually copied, not template.getTotalMilestones(), so the
                // denominator of completionPercentage can never disagree with the milestone list.
                .totalMilestones(steps.size())
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

        log.info("Generated roadmap id={} for studentId={} career='{}' with {} milestones",
                saved.getId(), studentId, saved.getCareerName(), steps.size());
    }

    @Override
    @Transactional(readOnly = true)
    public RoadmapResponse getMyRoadmap(Long studentId) {
        // A List, not an Optional: a student who takes a second assessment holds two IN_PROGRESS
        // roadmaps and an Optional finder would throw. DESC ordering makes the first the newest.
        List<StudentRoadmap> active =
                studentRoadmapRepository.findByStudentIdAndStatusOrderByStartedAtDesc(studentId, STATUS_IN_PROGRESS);

        if (active.isEmpty()) {
            throw new CustomException("No active roadmap found", HttpStatus.NOT_FOUND);
        }

        return toResponse(active.get(0));
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
