package com.careerbridge.student.repository;

import com.careerbridge.student.model.Education;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducationRepository extends JpaRepository<Education, Long> {

    List<Education> findByStudentProfileId(Long studentProfileId);
}
