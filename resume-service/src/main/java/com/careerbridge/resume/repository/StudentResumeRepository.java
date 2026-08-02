package com.careerbridge.resume.repository;

import com.careerbridge.resume.model.ResumeSummary;
import com.careerbridge.resume.model.StudentResume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentResumeRepository extends JpaRepository<StudentResume, Long> {

    /**
     * Metadata list for GET /my and GET /student/{id} -- the ResumeSummary projection excludes
     * pdfContent, so this never pulls a PDF into memory just to render a list.
     */
    List<ResumeSummary> findByStudentIdOrderByGeneratedAtDesc(Long studentId);

    /**
     * Entity form, used only to flip every prior resume's isDefault to false on a new generation.
     * Deliberately not the projection above: that write needs the full entity to save() with.
     */
    List<StudentResume> findAllByStudentId(Long studentId);

    /** Ownership check for a STUDENT reading or downloading their own resume. */
    Optional<StudentResume> findByIdAndStudentId(Long id, Long studentId);

    /** Next version number. Element 0 of the version-desc ordering is the current highest. */
    Optional<StudentResume> findTopByStudentIdOrderByVersionDesc(Long studentId);

    // findById(Long) is inherited from JpaRepository -- used for admin/recruiter reads and download,
    // where there is no studentId to scope by (RBAC already narrowed the caller to an allowed role).
}
