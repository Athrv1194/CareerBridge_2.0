package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.dto.JobApplicationResponse;
import com.careerbridge.recruiter.dto.UpdateApplicationStatusRequest;

import java.util.List;

public interface ApplicationService {

    /** STUDENT only. Refused if the job is inactive, past its deadline, or already applied to. */
    JobApplicationResponse applyToJob(String callerRole, Long studentId, Long jobId);

    /** STUDENT only. The caller's own applications, newest first. */
    List<JobApplicationResponse> getMyApplications(String callerRole, Long studentId);

    /** RECRUITER only, and only for a job they own. */
    List<JobApplicationResponse> getApplicationsForJob(String callerRole, Long recruiterId, Long jobId);

    /**
     * PLACEMENT_OFFICER, ORG_ADMIN or SUPER_ADMIN. Scoped to the students of callerOrgId, which
     * prs-service resolves -- it is the only service holding organizationId per student.
     */
    List<JobApplicationResponse> getApplicationsForOrgStudents(String callerRole, Long callerOrgId);

    /** RECRUITER only, and only for an application against a job they own. */
    JobApplicationResponse updateApplicationStatus(String callerRole, Long recruiterId,
                                                   Long applicationId,
                                                   UpdateApplicationStatusRequest request);
}
