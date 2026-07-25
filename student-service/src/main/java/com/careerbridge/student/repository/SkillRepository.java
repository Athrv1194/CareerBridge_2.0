package com.careerbridge.student.repository;

import com.careerbridge.student.model.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    List<Skill> findByStudentProfileId(Long studentProfileId);

    Boolean existsByStudentProfileIdAndSkillName(Long studentProfileId, String skillName);
}
