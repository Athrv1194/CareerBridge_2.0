package com.careerbridge.student.repository;

import com.careerbridge.student.model.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    List<Skill> findByStudentProfileId(Long studentProfileId);

    Boolean existsByStudentProfileIdAndSkillName(Long studentProfileId, String skillName);

    /** Batch load for candidate search: one query for every public profile's skills, not N. */
    List<Skill> findByStudentProfileIdIn(List<Long> studentProfileIds);

    /** See EducationRepository.deleteByIdAndStudentProfileId for why this returns long, not List. */
    long deleteByIdAndStudentProfileId(Long id, Long studentProfileId);
}
