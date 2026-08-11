package com.careerbridge.mentor.service;

import com.careerbridge.mentor.dto.BookSessionRequest;
import com.careerbridge.mentor.dto.CreateMentorProfileRequest;
import com.careerbridge.mentor.dto.CreateReviewRequest;
import com.careerbridge.mentor.dto.MentorProfileResponse;
import com.careerbridge.mentor.dto.MentorshipSessionResponse;
import com.careerbridge.mentor.dto.RespondToSessionRequest;
import com.careerbridge.mentor.dto.SessionReviewResponse;
import com.careerbridge.mentor.dto.UpdateMentorProfileRequest;

import java.util.List;

/**
 * All RBAC lives in the implementation of this interface -- never in the controller, never in the
 * gateway. The gateway validates the JWT and forwards identity; it knows nothing about who owns a
 * session row.
 */
public interface MentorService {

    // -- Mentor profile ---------------------------------------------------------------------------

    /** MENTOR only. 409 if this user already has a profile. */
    MentorProfileResponse createProfile(Long userId, String role, CreateMentorProfileRequest request);

    /** MENTOR only, own profile. Partial update -- null means leave unchanged. */
    MentorProfileResponse updateProfile(Long userId, String role, UpdateMentorProfileRequest request);

    /** MENTOR only. 404 before they have created one. */
    MentorProfileResponse getMyProfile(Long userId, String role);

    /** Any authenticated user -- a mentor profile is public by design. */
    MentorProfileResponse getProfileById(Long profileId);

    /**
     * Any authenticated user. careerPath and expertise are independent optional filters; when both
     * are supplied careerPath wins, and when neither is supplied every available mentor is returned
     * best-rated first.
     */
    List<MentorProfileResponse> browseMentors(String careerPath, String expertise);

    // -- Sessions ---------------------------------------------------------------------------------

    /** STUDENT only. 409 if this student already has a REQUESTED or ACCEPTED session with this mentor. */
    MentorshipSessionResponse bookSession(Long studentId, String role, BookSessionRequest request);

    /** MENTOR only, own session. ACCEPT requires a meeting link; both actions require REQUESTED. */
    MentorshipSessionResponse respondToSession(Long mentorUserId, String role, Long sessionId,
                                               RespondToSessionRequest request);

    /** MENTOR only, own session. Only an ACCEPTED session can be completed. */
    MentorshipSessionResponse completeSession(Long mentorUserId, String role, Long sessionId);

    /**
     * STUDENT or MENTOR, own session. A student may only cancel while still REQUESTED; a mentor may
     * cancel REQUESTED or ACCEPTED, since they are the one who has to show up.
     */
    MentorshipSessionResponse cancelSession(Long requesterId, String requesterRole, Long sessionId);

    List<MentorshipSessionResponse> getMySessionsAsStudent(Long studentId, String role);

    List<MentorshipSessionResponse> getMySessionsAsMentor(Long mentorUserId, String role);

    // -- Reviews ----------------------------------------------------------------------------------

    /** STUDENT only, own session, COMPLETED only, one per session. Recalculates the mentor's average. */
    SessionReviewResponse createReview(Long studentId, String role, Long sessionId,
                                       CreateReviewRequest request);

    /** Any authenticated user -- reviews are what make a public profile worth reading. */
    List<SessionReviewResponse> getReviewsForMentor(Long mentorProfileId);
}
