package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.dto.ExtendOfferRequest;
import com.careerbridge.recruiter.dto.JobApplicationResponse;
import com.careerbridge.recruiter.dto.OfferResponseRequest;
import com.careerbridge.recruiter.dto.ResumeFileBlob;
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

    /**
     * RECRUITER only, and only for an application against a job they own. Sets the application to
     * OFFERED and records the CTC and offer date.
     *
     * Re-callable to correct the CTC, but only until the student responds -- see the impl.
     */
    JobApplicationResponse extendOffer(String callerRole, Long recruiterId, Long applicationId,
                                       ExtendOfferRequest request);

    /**
     * STUDENT only, and only for their own application. Records ACCEPTED or DECLINED, exactly once.
     *
     * Deliberately separate from extendOffer and gated on a different role: nobody should be able
     * to accept a job offer on a student's behalf.
     */
    JobApplicationResponse respondToOffer(String callerRole, Long studentId, Long applicationId,
                                          OfferResponseRequest request);

    /** STUDENT only, and only for their own application. Replaces any résumé already attached. */
    void uploadResume(String callerRole, Long studentId, Long applicationId,
                      byte[] bytes, String contentType, String fileName);

    /**
     * The STUDENT who owns the application, the RECRUITER who owns its job, or a
     * PLACEMENT_OFFICER/ORG_ADMIN/SUPER_ADMIN -- the same roles already entitled to view the
     * application itself via getApplicationsForOrgStudents.
     */
    ResumeFileBlob getResume(String callerRole, Long userId, Long applicationId);
}
