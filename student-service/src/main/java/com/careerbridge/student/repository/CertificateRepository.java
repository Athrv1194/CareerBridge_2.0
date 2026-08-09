package com.careerbridge.student.repository;

import com.careerbridge.student.model.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    List<Certificate> findByStudentProfileId(Long studentProfileId);

    /** Ownership check for edit: a row that exists but belongs to another profile is a 404. */
    Optional<Certificate> findByIdAndStudentProfileId(Long id, Long studentProfileId);

    /** See EducationRepository.deleteByIdAndStudentProfileId for why this returns long, not List. */
    long deleteByIdAndStudentProfileId(Long id, Long studentProfileId);
}
