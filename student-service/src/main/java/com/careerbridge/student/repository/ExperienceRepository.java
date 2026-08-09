package com.careerbridge.student.repository;

import com.careerbridge.student.model.Experience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {

    List<Experience> findByStudentProfileIdOrderByStartDateDesc(Long studentProfileId);

    /** Ownership check for edit. */
    Optional<Experience> findByIdAndStudentProfileId(Long id, Long studentProfileId);

    /** See EducationRepository.deleteByIdAndStudentProfileId for why this returns long, not List. */
    long deleteByIdAndStudentProfileId(Long id, Long studentProfileId);
}
