package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.constants.RecruiterRoles;
import com.careerbridge.recruiter.dto.CompanyResponse;
import com.careerbridge.recruiter.dto.CreateCompanyRequest;
import com.careerbridge.recruiter.dto.UpdateCompanyRequest;
import com.careerbridge.recruiter.exception.CustomException;
import com.careerbridge.recruiter.model.Company;
import com.careerbridge.recruiter.repository.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * RBAC lives here, never in the controller or the gateway -- the gateway validates the JWT and
 * forwards identity, but knows nothing about which recruiter owns which company.
 */
@Service
public class CompanyServiceImpl implements CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyServiceImpl.class);

    private final CompanyRepository companyRepository;

    public CompanyServiceImpl(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Override
    @Transactional
    public CompanyResponse createCompany(String callerRole, Long recruiterId, CreateCompanyRequest request) {
        requireRecruiter(callerRole);

        // Fast path only. uk_company_recruiter_id is the real guarantee: two concurrent creates
        // can both pass this check, and the loser gets a DataIntegrityViolationException.
        if (companyRepository.existsByRecruiterId(recruiterId)) {
            throw new CustomException(
                    "You already have a company profile - use the update endpoint to modify it",
                    HttpStatus.BAD_REQUEST);
        }

        Company saved = companyRepository.save(Company.builder()
                .recruiterId(recruiterId)
                .name(request.getName())
                .industry(request.getIndustry())
                .website(request.getWebsite())
                .description(request.getDescription())
                .logoUrl(request.getLogoUrl())
                .build());

        log.info("Company {} created by recruiterId={}", saved.getId(), recruiterId);
        return toResponse(saved);
    }

    /**
     * Loads by (id, recruiterId) rather than by id then comparing, so another recruiter's company
     * is a 404 rather than a 403. Unlike roadmap-service's completeMilestone, this endpoint owes
     * the caller no existence signal: a recruiter has exactly one company and never has a
     * legitimate reason to address another's.
     */
    @Override
    @Transactional
    public CompanyResponse updateCompany(String callerRole, Long recruiterId, Long companyId,
                                         UpdateCompanyRequest request) {
        requireRecruiter(callerRole);

        Company company = companyRepository.findByIdAndRecruiterId(companyId, recruiterId)
                .orElseThrow(() -> new CustomException("Company not found", HttpStatus.NOT_FOUND));

        // Null means "leave unchanged", not "clear" -- a full-replace update would wipe the
        // website and logo every time someone corrected a typo in the company name.
        if (request.getName() != null) {
            company.setName(request.getName());
        }
        if (request.getIndustry() != null) {
            company.setIndustry(request.getIndustry());
        }
        if (request.getWebsite() != null) {
            company.setWebsite(request.getWebsite());
        }
        if (request.getDescription() != null) {
            company.setDescription(request.getDescription());
        }
        if (request.getLogoUrl() != null) {
            company.setLogoUrl(request.getLogoUrl());
        }

        return toResponse(companyRepository.save(company));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyResponse getCompanyById(Long companyId) {
        return toResponse(companyRepository.findById(companyId)
                .orElseThrow(() -> new CustomException("Company not found", HttpStatus.NOT_FOUND)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyResponse> getMyCompanies(String callerRole, Long recruiterId) {
        requireRecruiter(callerRole);

        return companyRepository.findByRecruiterId(recruiterId).stream()
                .map(this::toResponse)
                .toList();
    }

    private void requireRecruiter(String callerRole) {
        if (!RecruiterRoles.RECRUITER.equals(callerRole)) {
            throw new CustomException("Only a RECRUITER may manage company profiles",
                    HttpStatus.FORBIDDEN);
        }
    }

    private CompanyResponse toResponse(Company company) {
        return CompanyResponse.builder()
                .id(company.getId())
                .recruiterId(company.getRecruiterId())
                .name(company.getName())
                .industry(company.getIndustry())
                .website(company.getWebsite())
                .description(company.getDescription())
                .logoUrl(company.getLogoUrl())
                .createdAt(company.getCreatedAt())
                .updatedAt(company.getUpdatedAt())
                .build();
    }
}
