package com.careerbridge.student.repository;

import com.careerbridge.student.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByStudentProfileId(Long studentProfileId);

    /** Ownership check for edit and for the cover-image endpoints. */
    Optional<Project> findByIdAndStudentProfileId(Long id, Long studentProfileId);

    /** See EducationRepository.deleteByIdAndStudentProfileId for why this returns long, not List. */
    long deleteByIdAndStudentProfileId(Long id, Long studentProfileId);
}
