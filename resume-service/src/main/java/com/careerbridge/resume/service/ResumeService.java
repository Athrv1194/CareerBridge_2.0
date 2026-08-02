package com.careerbridge.resume.service;

import com.careerbridge.resume.dto.ResumeDownload;
import com.careerbridge.resume.dto.ResumeResponse;

import java.util.List;

public interface ResumeService {

    /**
     * STUDENT only. Fetches the caller's profile, scores it, renders a PDF, stores it as a new
     * version, and publishes resume.generated. Every generation is a new row; nothing is
     * overwritten.
     */
    ResumeResponse generateResume(String callerRole, Long studentId);

    /** STUDENT only. The caller's own resumes, newest first. Metadata only, never PDF bytes. */
    List<ResumeResponse> getMyResumes(String callerRole, Long studentId);

    /** STUDENT (own only), RECRUITER, PLACEMENT_OFFICER, ORG_ADMIN, SUPER_ADMIN. */
    ResumeResponse getResumeById(String callerRole, Long callerId, Long resumeId);

    /**
     * Same RBAC as getResumeById. Returns the PDF bytes together with the stored fileName so the
     * controller can set Content-Disposition without a second read.
     */
    ResumeDownload downloadResume(String callerRole, Long callerId, Long resumeId);

    /**
     * STUDENT only, own resumes only. If the deleted resume was the default and others remain,
     * the newest survivor is promoted so a student is never left with no default.
     */
    void deleteResume(String callerRole, Long studentId, Long resumeId);

    /** RECRUITER, PLACEMENT_OFFICER, ORG_ADMIN, SUPER_ADMIN. A STUDENT is refused. */
    List<ResumeResponse> getResumesByStudentId(String callerRole, Long targetStudentId);
}
