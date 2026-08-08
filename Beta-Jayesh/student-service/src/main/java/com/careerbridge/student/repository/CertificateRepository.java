package com.careerbridge.student.repository;

import com.careerbridge.student.model.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    List<Certificate> findByStudentProfileId(Long studentProfileId);
}
