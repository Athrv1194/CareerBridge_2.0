package com.careerbridge.roadmap.repository;

import com.careerbridge.roadmap.model.StudentRoadmap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRoadmapRepository extends JpaRepository<StudentRoadmap, Long> {

    // Safe Optional only because uk_student_roadmap_career enforces uniqueness.
    Optional<StudentRoadmap> findByStudentIdAndCareerNameIgnoreCase(Long studentId, String careerName);

    // List not Optional: a student can have roadmaps for multiple careers. No status filter --
    // filtering to IN_PROGRESS caused 404 on the very endpoint that shows a finished roadmap.
    List<StudentRoadmap> findByStudentIdOrderByStartedAtDesc(Long studentId);

    // NULLS LAST: rows predating activatedAt column fall back to startedAt-DESC order.
    @Query("SELECT r FROM StudentRoadmap r WHERE r.studentId = :studentId "
            + "ORDER BY r.activatedAt DESC NULLS LAST, r.startedAt DESC")
    List<StudentRoadmap> findByStudentIdOrderByActivatedThenStarted(@Param("studentId") Long studentId);
}
