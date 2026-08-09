package com.careerbridge.student.repository;

import com.careerbridge.student.model.Education;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EducationRepository extends JpaRepository<Education, Long> {

    List<Education> findByStudentProfileId(Long studentProfileId);

    /** Ownership check for edit: a row that exists but belongs to another profile is a 404. */
    Optional<Education> findByIdAndStudentProfileId(Long id, Long studentProfileId);

    /**
     * A derived delete returning long, not List<T> (which would run findAllAndRemove and load the
     * row first). 0 means either no such id or it belongs to another profile -- both collapse to
     * 404 in the service, same shape ai-coach-service's ChatSessionRepository.deleteByIdAndStudentId
     * uses and for the same reason: a student has no legitimate reason to address another's row.
     */
    long deleteByIdAndStudentProfileId(Long id, Long studentProfileId);
}
