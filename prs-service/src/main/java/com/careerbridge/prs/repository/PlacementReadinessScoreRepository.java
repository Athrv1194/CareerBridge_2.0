package com.careerbridge.prs.repository;

import com.careerbridge.prs.model.PlacementReadinessScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlacementReadinessScoreRepository extends JpaRepository<PlacementReadinessScore, Long> {

    /**
     * An Optional is safe here, unlike the List-returning finders in roadmap-service and
     * recommendation-service, because uk_prs_student_id genuinely makes student_id unique -- one
     * PRS row per student, forever. That constraint is what licenses the single-result return type;
     * drop it and this starts throwing IncorrectResultSizeDataAccessException.
     */
    Optional<PlacementReadinessScore> findByStudentId(Long studentId);

    /** Fast path for the student.registered consumer's idempotency check. */
    boolean existsByStudentId(Long studentId);

    /** SUPER_ADMIN leaderboard: every student, highest first. */
    List<PlacementReadinessScore> findAllByOrderByTotalScoreDesc();

    /**
     * ORG_ADMIN leaderboard, scoped to the caller's own tenant. Rows with a null organization_id
     * never match, which is deliberate -- see PlacementReadinessScore.organizationId.
     */
    List<PlacementReadinessScore> findByOrganizationIdOrderByTotalScoreDesc(Long organizationId);
}
