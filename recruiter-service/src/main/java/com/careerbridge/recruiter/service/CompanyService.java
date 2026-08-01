package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.dto.CompanyResponse;
import com.careerbridge.recruiter.dto.CreateCompanyRequest;
import com.careerbridge.recruiter.dto.UpdateCompanyRequest;

import java.util.List;

public interface CompanyService {

    /** RECRUITER only. One company per recruiter -- a second attempt is a 400, not a second row. */
    CompanyResponse createCompany(String callerRole, Long recruiterId, CreateCompanyRequest request);

    /** RECRUITER only, and only their own company: a foreign companyId is a 404, never a 403. */
    CompanyResponse updateCompany(String callerRole, Long recruiterId, Long companyId,
                                  UpdateCompanyRequest request);

    /** No role restriction: a student browsing a job needs to see who posted it. */
    CompanyResponse getCompanyById(Long companyId);

    /** RECRUITER only. */
    List<CompanyResponse> getMyCompanies(String callerRole, Long recruiterId);
}
