package com.careerbridge.recruiter.repository;

import com.careerbridge.recruiter.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findByRecruiterId(Long recruiterId);

    /** Ownership check: prevents a recruiter editing another recruiter's company. */
    Optional<Company> findByIdAndRecruiterId(Long id, Long recruiterId);

    /** Fast path for the one-company-per-recruiter guard; uk_company_recruiter_id is the real one. */
    boolean existsByRecruiterId(Long recruiterId);
}
