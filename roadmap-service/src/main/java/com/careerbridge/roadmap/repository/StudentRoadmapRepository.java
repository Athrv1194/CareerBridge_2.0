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

    /**
     * Backs GET /api/roadmap/my, which takes the first -- the newest, by the DESC ordering.
     *
     * A List, NOT an Optional. A student who takes a second assessment gets a second recommendation
     * and therefore a second roadmap, so this legitimately matches more than one row and a
     * single-result finder would throw IncorrectResultSizeDataAccessException on exactly the second
     * assessment. Same reasoning as recommendation-service's findByUserIdAndIsActiveTrue.
     *
     * Deliberately not filtered by status. A ...AndStatusOrderByStartedAtDesc variant existed here
     * and getMyRoadmap used it to select IN_PROGRESS only, which meant completing the last milestone
     * flipped the roadmap to COMPLETED and 404'd the endpoint that displays it. Do not reintroduce
     * the filter without giving the student some other way to see a finished roadmap.
     */
    List<StudentRoadmap> findByStudentIdOrderByStartedAtDesc(Long studentId);
}
