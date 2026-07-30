package com.careerbridge.roadmap.repository;

import com.careerbridge.roadmap.model.MilestoneTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MilestoneTemplateRepository extends JpaRepository<MilestoneTemplate, Long> {

    /**
     * Ordered explicitly. The @OneToMany on RoadmapTemplate carries no @OrderBy, so its collection
     * comes back in whatever order the database returns -- generating a roadmap from that would
     * number the student's milestones arbitrarily.
     */
    List<MilestoneTemplate> findByRoadmapTemplateIdOrderByOrderIndexAsc(Long templateId);
}
