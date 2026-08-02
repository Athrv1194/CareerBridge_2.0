package com.careerbridge.prs.service;

import com.careerbridge.prs.config.RabbitMQConfig;
import com.careerbridge.prs.dto.PrsBreakdown;
import com.careerbridge.prs.dto.PrsLeaderboardEntry;
import com.careerbridge.prs.dto.PrsResponse;
import com.careerbridge.prs.event.PrsUpdatedEvent;
import com.careerbridge.prs.exception.CustomException;
import com.careerbridge.prs.model.PlacementReadinessScore;
import com.careerbridge.prs.repository.PlacementReadinessScoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PrsServiceImpl implements PrsService {

    private static final Logger log = LoggerFactory.getLogger(PrsServiceImpl.class);

    /** Matches auth-service's Role enum. Strings, because that is what arrives in X-User-Role. */
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ORG_ADMIN = "ORG_ADMIN";

    /**
     * The weights sum to 1.00. Until 2026-08-01 they summed to 0.90, with RESUME_WEIGHT unallocated
     * ("reserved for the future resume builder") -- resume-service is that builder, and this weight
     * is now live. A student who has never generated a resume keeps resumeScore at its entity
     * default of 0.0, so no existing score changes on its own; totalScore only rises once a resume
     * actually exists for that student.
     */
    private static final double ASSESSMENT_WEIGHT = 0.40;
    private static final double ROADMAP_WEIGHT = 0.30;
    private static final double PROFILE_WEIGHT = 0.20;
    private static final double RESUME_WEIGHT = 0.10;

    private static final int ASSESSMENT_WEIGHT_PCT = 40;
    private static final int ROADMAP_WEIGHT_PCT = 30;
    private static final int PROFILE_WEIGHT_PCT = 20;
    private static final int RESUME_WEIGHT_PCT = 10;

    private static final double GRADE_A_MIN = 80.0;
    private static final double GRADE_B_MIN = 60.0;
    private static final double GRADE_C_MIN = 40.0;
    private static final double GRADE_D_MIN = 20.0;

    private final PlacementReadinessScoreRepository prsRepository;
    private final StudentServiceClient studentServiceClient;
    private final RabbitTemplate rabbitTemplate;

    public PrsServiceImpl(PlacementReadinessScoreRepository prsRepository,
                          StudentServiceClient studentServiceClient,
                          RabbitTemplate rabbitTemplate) {
        this.prsRepository = prsRepository;
        this.studentServiceClient = studentServiceClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    // ---------------------------------------------------------------------------------------------
    // Event-driven writes
    // ---------------------------------------------------------------------------------------------

    /**
     * @Transactional is safe alongside the consumer's fail-soft catch because the transaction opens
     * and closes inside this proxied call -- by the time an exception reaches the listener it has
     * already rolled back, so the catch is not swallowing a still-open rollback-only transaction.
     */
    @Override
    @Transactional
    public void initialisePrs(Long studentId, Long organizationId) {
        if (studentId == null) {
            log.warn("Ignoring {} with no userId", RabbitMQConfig.STUDENT_REGISTERED_ROUTING_KEY);
            return;
        }

        // Fast path only. uk_prs_student_id is the real guarantee: RabbitMQ is at-least-once, so a
        // redelivery can race this check and the loser raises DataIntegrityViolationException,
        // which the consumer's fail-soft catch discards.
        if (prsRepository.existsByStudentId(studentId)) {
            log.info("PRS record already exists for studentId={}, skipping", studentId);
            return;
        }

        prsRepository.save(PlacementReadinessScore.builder()
                .studentId(studentId)
                .organizationId(organizationId)
                .build());

        log.info("Initialised PRS record for studentId={} organizationId={}", studentId, organizationId);
    }

    @Override
    @Transactional
    public void updateAssessmentScore(Long studentId, Double matchPercentage) {
        if (studentId == null || matchPercentage == null) {
            log.warn("Ignoring {} with null studentId or matchPercentage",
                    RabbitMQConfig.RECOMMENDATION_GENERATED_ROUTING_KEY);
            return;
        }

        PlacementReadinessScore prs = loadOrCreate(studentId);
        prs.setAssessmentScore(matchPercentage);

        recomputeAndSave(prs, "assessment");
    }

    @Override
    @Transactional
    public void updateRoadmapScore(Long studentId, Double completionPercentage) {
        if (studentId == null || completionPercentage == null) {
            log.warn("Ignoring {} with null studentId or completionPercentage",
                    RabbitMQConfig.ROADMAP_UPDATED_ROUTING_KEY);
            return;
        }

        PlacementReadinessScore prs = loadOrCreate(studentId);
        prs.setRoadmapScore(completionPercentage);

        recomputeAndSave(prs, "roadmap");
    }

    @Override
    @Transactional
    public void updateResumeScore(Long studentId, Double atsScore) {
        if (studentId == null || atsScore == null) {
            log.warn("Ignoring {} with null studentId or atsScore",
                    RabbitMQConfig.RESUME_GENERATED_ROUTING_KEY);
            return;
        }

        PlacementReadinessScore prs = loadOrCreate(studentId);
        prs.setResumeScore(atsScore);

        recomputeAndSave(prs, "resume");
    }

    /**
     * Defensive creation, deliberately fail-open. A recommendation or roadmap event for a student
     * with no PRS row means student.registered was missed -- most likely because they registered
     * before this service first ran. Creating the row here means their score still works; refusing
     * would leave them permanently invisible with no way to recover short of re-registering.
     *
     * The row created here has a null organizationId, since only student.registered carries one. It
     * therefore never appears on an ORG_ADMIN's scoped leaderboard, which is the right trade: omit
     * a student from their own college's board rather than risk placing them on another's.
     *
     * Known race, accepted: if student.registered and recommendation.generated arrive at almost the
     * same instant, this insert and initialisePrs's can collide. uk_prs_student_id stops the
     * duplicate row, but the loser's exception is swallowed by the consumer's fail-soft catch and
     * that one score update is lost until the next event. Registration precedes any assessment by
     * minutes in practice, so the window is effectively unreachable -- not worth retry machinery.
     */
    private PlacementReadinessScore loadOrCreate(Long studentId) {
        return prsRepository.findByStudentId(studentId)
                .orElseGet(() -> {
                    log.warn("No PRS record for studentId={}; creating one defensively "
                            + "(student.registered was likely missed)", studentId);
                    return PlacementReadinessScore.builder()
                            .studentId(studentId)
                            .build();
                });
    }

    /**
     * Refreshes the profile input, recomputes the derived fields, saves, and publishes.
     *
     * The profile refresh happens on every score update rather than on its own schedule because
     * student-service publishes no event when a profile changes -- this is the only moment PRS
     * learns about it. Costs one synchronous call per event.
     */
    private void recomputeAndSave(PlacementReadinessScore prs, String trigger) {
        refreshProfileScore(prs);

        prs.setTotalScore(computeTotalScore(prs));
        prs.setGrade(computeGrade(prs.getTotalScore()));

        PlacementReadinessScore saved = prsRepository.save(prs);

        log.info("PRS updated for studentId={} via {}: assessment={} roadmap={} profile={} -> total={} grade={}",
                saved.getStudentId(), trigger, saved.getAssessmentScore(), saved.getRoadmapScore(),
                saved.getProfileScore(), saved.getTotalScore(), saved.getGrade());

        publishPrsUpdated(saved);
    }

    /**
     * Overwrites profileScore only when the fetch actually succeeded.
     *
     * StudentServiceClient.PROFILE_FETCH_FAILED (-1.0) means "keep the last known value" -- writing
     * it would turn a transient student-service outage into a permanently wrong score, and would
     * drag totalScore below zero into the bargain.
     */
    private void refreshProfileScore(PlacementReadinessScore prs) {
        double fetched = studentServiceClient.fetchProfileScore(prs.getStudentId());

        if (fetched == StudentServiceClient.PROFILE_FETCH_FAILED) {
            log.warn("Keeping last known profileScore={} for studentId={}",
                    prs.getProfileScore(), prs.getStudentId());
            return;
        }

        prs.setProfileScore(fetched);
    }

    // ---------------------------------------------------------------------------------------------
    // Scoring
    // ---------------------------------------------------------------------------------------------

    /** Always recomputed from the four stored inputs, never incremented, so it cannot drift. */
    private double computeTotalScore(PlacementReadinessScore prs) {
        double total = nullSafe(prs.getAssessmentScore()) * ASSESSMENT_WEIGHT
                + nullSafe(prs.getRoadmapScore()) * ROADMAP_WEIGHT
                + nullSafe(prs.getProfileScore()) * PROFILE_WEIGHT
                + nullSafe(prs.getResumeScore()) * RESUME_WEIGHT;

        return round2(total);
    }

    /**
     * Boundaries are inclusive at the lower end: exactly 80.0 is an A, 79.9 is a B. Every boundary
     * is pinned in both directions by computeGrade_BoundaryValues_CorrectGrade.
     */
    private String computeGrade(Double totalScore) {
        double score = nullSafe(totalScore);

        if (score >= GRADE_A_MIN) {
            return "A";
        }
        if (score >= GRADE_B_MIN) {
            return "B";
        }
        if (score >= GRADE_C_MIN) {
            return "C";
        }
        if (score >= GRADE_D_MIN) {
            return "D";
        }
        return "F";
    }

    /** 0.40 x 33.33 is 13.332 raw; nothing downstream wants that many digits. */
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double nullSafe(Double value) {
        return value == null ? 0.0 : value;
    }

    // ---------------------------------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PrsResponse getMyPrs(Long studentId) {
        return toResponse(prsRepository.findByStudentId(studentId)
                .orElseThrow(() -> new CustomException("No placement readiness score found",
                        HttpStatus.NOT_FOUND)));
    }

    @Override
    @Transactional(readOnly = true)
    public PrsResponse getPrsByStudentId(Long callerId, String callerRole, Long targetStudentId) {
        requireAdmin(callerRole);

        // Placement data about a named student is worth an access trail, which is the only reason
        // callerId is carried this far down.
        log.info("callerId={} role={} read the PRS of studentId={}", callerId, callerRole, targetStudentId);

        return toResponse(prsRepository.findByStudentId(targetStudentId)
                .orElseThrow(() -> new CustomException("No placement readiness score found",
                        HttpStatus.NOT_FOUND)));
    }

    /**
     * SUPER_ADMIN ranks globally; ORG_ADMIN ranks within their own organization only.
     *
     * The scoping matters: without it a college admin reads every other college's student scores.
     * organization-service set the precedent by refusing ORG_ADMIN a cross-tenant list outright;
     * here a scoped list is genuinely useful, so it is narrowed rather than refused -- but it is
     * narrowed in the service, never left to the caller to filter.
     *
     * An ORG_ADMIN whose token carries no organization gets an empty list, not everyone's. That
     * combination should not occur (auth-service always sets organizationId for an ORG_ADMIN), and
     * failing closed is the only safe reading of it.
     */
    @Override
    @Transactional(readOnly = true)
    public List<PrsLeaderboardEntry> getLeaderboard(String callerRole, Long callerOrgId) {
        requireAdmin(callerRole);

        List<PlacementReadinessScore> rows;
        if (ROLE_SUPER_ADMIN.equals(callerRole)) {
            rows = prsRepository.findAllByOrderByTotalScoreDesc();
        } else if (callerOrgId == null) {
            log.warn("ORG_ADMIN requested the leaderboard with no X-User-Org-Id; returning empty");
            rows = List.of();
        } else {
            rows = prsRepository.findByOrganizationIdOrderByTotalScoreDesc(callerOrgId);
        }

        List<PrsLeaderboardEntry> leaderboard = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            PlacementReadinessScore row = rows.get(i);
            leaderboard.add(PrsLeaderboardEntry.builder()
                    // 1-based, from position in this response. Never stored: the same student holds
                    // a different rank in a global list than in their own college's.
                    .rank(i + 1)
                    .studentId(row.getStudentId())
                    .totalScore(row.getTotalScore())
                    .grade(row.getGrade())
                    .build());
        }

        return leaderboard;
    }

    // ---------------------------------------------------------------------------------------------
    // Authorization
    // ---------------------------------------------------------------------------------------------

    /**
     * Another student's score, and the leaderboard, are admin surfaces. A student reads their own
     * through GET /api/prs/my, which needs no role at all. RBAC lives here, never in the controller
     * or the gateway -- the gateway forwards identity but knows nothing about what it grants.
     */
    private void requireAdmin(String callerRole) {
        if (!ROLE_SUPER_ADMIN.equals(callerRole) && !ROLE_ORG_ADMIN.equals(callerRole)) {
            throw new CustomException("Only SUPER_ADMIN or ORG_ADMIN may perform this operation",
                    HttpStatus.FORBIDDEN);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Events and mapping
    // ---------------------------------------------------------------------------------------------

    /**
     * Fail-soft. The row is already committed by the time this runs, so rethrowing would report a
     * failure for work that actually happened -- and the redelivered event would then recompute the
     * identical score for no gain. Nothing consumes prs.updated yet, so a broker outage here costs
     * a future dashboard refresh, not the student's score.
     */
    private void publishPrsUpdated(PlacementReadinessScore prs) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.PRS_UPDATED_ROUTING_KEY,
                    PrsUpdatedEvent.builder()
                            .studentId(prs.getStudentId())
                            .totalScore(prs.getTotalScore())
                            .grade(prs.getGrade())
                            .updatedAt(LocalDateTime.now())
                            .build());
        } catch (Exception ex) {
            log.error("Failed to publish {} for studentId={}",
                    RabbitMQConfig.PRS_UPDATED_ROUTING_KEY, prs.getStudentId(), ex);
        }
    }

    private PrsResponse toResponse(PlacementReadinessScore prs) {
        double assessment = nullSafe(prs.getAssessmentScore());
        double roadmap = nullSafe(prs.getRoadmapScore());
        double profile = nullSafe(prs.getProfileScore());
        double resume = nullSafe(prs.getResumeScore());

        PrsBreakdown breakdown = PrsBreakdown.builder()
                .assessmentWeight(ASSESSMENT_WEIGHT_PCT)
                .assessmentScore(assessment)
                .assessmentContribution(round2(assessment * ASSESSMENT_WEIGHT))
                .roadmapWeight(ROADMAP_WEIGHT_PCT)
                .roadmapScore(roadmap)
                .roadmapContribution(round2(roadmap * ROADMAP_WEIGHT))
                .profileWeight(PROFILE_WEIGHT_PCT)
                .profileScore(profile)
                .profileContribution(round2(profile * PROFILE_WEIGHT))
                .resumeWeight(RESUME_WEIGHT_PCT)
                .resumeScore(resume)
                .resumeContribution(round2(resume * RESUME_WEIGHT))
                .totalScore(prs.getTotalScore())
                .build();

        return PrsResponse.builder()
                .studentId(prs.getStudentId())
                .assessmentScore(assessment)
                .roadmapScore(roadmap)
                .profileScore(profile)
                .resumeScore(resume)
                .totalScore(prs.getTotalScore())
                .grade(prs.getGrade())
                .breakdown(breakdown)
                .lastUpdatedAt(prs.getLastUpdatedAt())
                .build();
    }
}
