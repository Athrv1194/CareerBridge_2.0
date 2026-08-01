package com.careerbridge.recruiter.service;

import com.careerbridge.recruiter.dto.InterviewResponse;
import com.careerbridge.recruiter.dto.ScheduleInterviewRequest;
import com.careerbridge.recruiter.dto.UpdateInterviewRequest;

import java.util.List;

public interface InterviewService {

    /**
     * RECRUITER only, own jobs only. The applicant must be SHORTLISTED or INTERVIEWED.
     *
     * Multiple rounds per application are allowed by design, which is why INTERVIEWED is accepted
     * as well: after round one the recruiter moves the applicant to INTERVIEWED, and requiring
     * SHORTLISTED alone would make round two impossible.
     */
    InterviewResponse scheduleInterview(String callerRole, Long recruiterId, Long applicationId,
                                        ScheduleInterviewRequest request);

    /** RECRUITER only. Every interview across every job the caller owns, soonest first. */
    List<InterviewResponse> getMyInterviews(String callerRole, Long recruiterId);

    /** RECRUITER only, own jobs only. Partial update; a CANCELLED interview is immutable. */
    InterviewResponse updateInterview(String callerRole, Long recruiterId, Long interviewId,
                                      UpdateInterviewRequest request);
}
