package com.careerbridge.roadmap.repository;

import com.careerbridge.roadmap.model.StudentMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentMilestoneRepository extends JpaRepository<StudentMilestone, Long> {

    List<StudentMilestone> findByStudentRoadmapIdOrderByOrderIndexAsc(Long roadmapId);

    /**
     * completedMilestones is recomputed from this after every completion rather than incremented in
     * memory, so the stored counter cannot drift from the rows it summarises.
     *
     * There is deliberately no findByIdAndStudentId here. It cannot distinguish "this milestone
     * belongs to someone else" (403) from "no such milestone" (404) -- both come back as an empty
     * Optional -- and completeMilestone owes the caller both. It loads by id and compares the owner
     * with Objects.equals instead.
     */
    long countByStudentRoadmapIdAndIsCompletedTrue(Long roadmapId);
}
