package com.careerbridge.roadmap.repository;

import com.careerbridge.roadmap.model.StudentRoadmap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRoadmapRepository extends JpaRepository<StudentRoadmap, Long> {

    /**
     * The idempotency fast path for a redelivered recommendation.generated. Optional is safe here
     * only because of the unique constraint on (student_id, recommendation_id) -- without it two
     * racing deliveries could both insert and this would start throwing.
     */
    Optional<StudentRoadmap> findByStudentIdAndRecommendationId(Long studentId, Long recommendationId);

    List<StudentRoadmap> findByStudentIdOrderByStartedAtDesc(Long studentId);

    /**
     * A List, NOT an Optional. A student who takes a second assessment gets a second recommendation
     * and therefore a second roadmap, and the earlier one stays IN_PROGRESS -- so this legitimately
     * matches more than one row and an Optional finder would throw
     * IncorrectResultSizeDataAccessException. getMyRoadmap takes the first, which the DESC ordering
     * makes the newest. Same reasoning as recommendation-service's findByUserIdAndIsActiveTrue.
     */
    List<StudentRoadmap> findByStudentIdAndStatusOrderByStartedAtDesc(Long studentId, String status);
}
