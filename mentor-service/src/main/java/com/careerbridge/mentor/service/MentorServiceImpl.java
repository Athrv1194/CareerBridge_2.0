package com.careerbridge.mentor.service;

import com.careerbridge.mentor.dto.BookSessionRequest;
import com.careerbridge.mentor.dto.CreateMentorProfileRequest;
import com.careerbridge.mentor.dto.CreateReviewRequest;
import com.careerbridge.mentor.dto.MentorProfileResponse;
import com.careerbridge.mentor.dto.MentorshipSessionResponse;
import com.careerbridge.mentor.dto.RespondToSessionRequest;
import com.careerbridge.mentor.dto.SessionReviewResponse;
import com.careerbridge.mentor.dto.UpdateMentorProfileRequest;
import com.careerbridge.mentor.event.SessionAcceptedEvent;
import com.careerbridge.mentor.event.SessionBookedEvent;
import com.careerbridge.mentor.event.SessionCompletedEvent;
import com.careerbridge.mentor.exception.CustomException;
import com.careerbridge.mentor.messaging.MentorEventPublisher;
import com.careerbridge.mentor.model.MentorProfile;
import com.careerbridge.mentor.model.MentorshipSession;
import com.careerbridge.mentor.model.SessionReview;
import com.careerbridge.mentor.repository.MentorProfileRepository;
import com.careerbridge.mentor.repository.MentorshipSessionRepository;
import com.careerbridge.mentor.repository.SessionReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Every authorization decision in mentor-service, in one place.
 *
 * Two distinct not-authorized shapes, chosen per endpoint rather than uniformly:
 *
 *  - 403 when the caller addressed a REAL row that is not theirs (a session belonging to another
 *    mentor, someone else's application to respond to). Flattening that to 404 would be misleading:
 *    the row exists and the caller can see it exists from their own list.
 *  - 404 when the caller has no legitimate reason to address that id at all.
 *
 * Same split recruiter-service settled on, and the reasoning is documented there too.
 *
 * Objects.equals for every boxed-Long comparison, never ==. Both sides are Long objects well
 * outside the Integer cache, so == is false for exactly the ids these checks exist to allow.
 */
@Service
public class MentorServiceImpl implements MentorService {

    private static final Logger log = LoggerFactory.getLogger(MentorServiceImpl.class);

    private static final String ROLE_MENTOR = "MENTOR";
    private static final String ROLE_STUDENT = "STUDENT";

    private static final String ACTION_ACCEPT = "ACCEPT";
    private static final String ACTION_DECLINE = "DECLINE";

    private static final int DEFAULT_DURATION_MINUTES = 30;

    /**
     * The statuses that block a second booking with the same mentor. COMPLETED, DECLINED and
     * CANCELLED are deliberately absent -- a finished or refused session must not stop a student
     * coming back to the same mentor later.
     */
    private static final List<String> ACTIVE_SESSION_STATUSES =
            List.of(MentorshipSession.STATUS_REQUESTED, MentorshipSession.STATUS_ACCEPTED);

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorshipSessionRepository sessionRepository;
    private final SessionReviewRepository reviewRepository;
    private final MentorEventPublisher eventPublisher;

    public MentorServiceImpl(MentorProfileRepository mentorProfileRepository,
                             MentorshipSessionRepository sessionRepository,
                             SessionReviewRepository reviewRepository,
                             MentorEventPublisher eventPublisher) {
        this.mentorProfileRepository = mentorProfileRepository;
        this.sessionRepository = sessionRepository;
        this.reviewRepository = reviewRepository;
        this.eventPublisher = eventPublisher;
    }

    // ---------------------------------------------------------------------------------------------
    // Mentor profile
    // ---------------------------------------------------------------------------------------------

    @Override
    @Transactional
    public MentorProfileResponse createProfile(Long userId, String role,
                                               CreateMentorProfileRequest request) {
        requireRole(role, ROLE_MENTOR, "Only a MENTOR may create a mentor profile");

        // Fast path only. uk_mentor_profile_user is the real guarantee -- two concurrent requests
        // race this check and the loser hits the constraint.
        if (mentorProfileRepository.existsByUserId(userId)) {
            throw new CustomException("You already have a mentor profile", HttpStatus.CONFLICT);
        }

        MentorProfile profile = mentorProfileRepository.save(MentorProfile.builder()
                .userId(userId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .bio(request.getBio())
                .currentCompany(request.getCurrentCompany())
                .currentRole(request.getCurrentRole())
                .yearsOfExperience(request.getYearsOfExperience())
                .expertiseAreas(request.getExpertiseAreas())
                .careerPaths(request.getCareerPaths())
                .linkedinUrl(request.getLinkedinUrl())
                .build());

        log.info("Created mentor profile id={} for userId={}", profile.getId(), userId);
        return toProfileResponse(profile);
    }

    @Override
    @Transactional
    public MentorProfileResponse updateProfile(Long userId, String role,
                                               UpdateMentorProfileRequest request) {
        requireRole(role, ROLE_MENTOR, "Only a MENTOR may update a mentor profile");

        MentorProfile profile = requireOwnProfile(userId);

        // Null means "leave unchanged", never "clear" -- a partial update that nulled unset fields
        // would wipe a mentor's bio every time they toggled availability.
        if (request.getBio() != null) {
            profile.setBio(request.getBio());
        }
        if (request.getCurrentCompany() != null) {
            profile.setCurrentCompany(request.getCurrentCompany());
        }
        if (request.getCurrentRole() != null) {
            profile.setCurrentRole(request.getCurrentRole());
        }
        if (request.getYearsOfExperience() != null) {
            profile.setYearsOfExperience(request.getYearsOfExperience());
        }
        if (request.getExpertiseAreas() != null) {
            profile.setExpertiseAreas(request.getExpertiseAreas());
        }
        if (request.getCareerPaths() != null) {
            profile.setCareerPaths(request.getCareerPaths());
        }
        if (request.getLinkedinUrl() != null) {
            profile.setLinkedinUrl(request.getLinkedinUrl());
        }
        if (request.getIsAvailable() != null) {
            profile.setIsAvailable(request.getIsAvailable());
        }

        return toProfileResponse(mentorProfileRepository.save(profile));
    }

    @Override
    @Transactional(readOnly = true)
    public MentorProfileResponse getMyProfile(Long userId, String role) {
        requireRole(role, ROLE_MENTOR, "Only a MENTOR has a mentor profile");
        return toProfileResponse(requireOwnProfile(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public MentorProfileResponse getProfileById(Long profileId) {
        MentorProfile profile = mentorProfileRepository.findById(profileId)
                .orElseThrow(() -> new CustomException("Mentor profile not found", HttpStatus.NOT_FOUND));
        return toProfileResponse(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MentorProfileResponse> browseMentors(String careerPath, String expertise) {
        List<MentorProfile> profiles;

        if (hasText(careerPath)) {
            profiles = mentorProfileRepository
                    .findByCareerPathsContainingIgnoreCaseAndIsAvailableTrue(careerPath.trim());
        } else if (hasText(expertise)) {
            profiles = mentorProfileRepository
                    .findByExpertiseAreasContainingIgnoreCaseAndIsAvailableTrue(expertise.trim());
        } else {
            profiles = mentorProfileRepository.findByIsAvailableTrueOrderByAverageRatingDesc();
        }

        return profiles.stream().map(this::toProfileResponse).toList();
    }

    // ---------------------------------------------------------------------------------------------
    // Sessions
    // ---------------------------------------------------------------------------------------------

    @Override
    @Transactional
    public MentorshipSessionResponse bookSession(Long studentId, String role, BookSessionRequest request) {
        requireRole(role, ROLE_STUDENT, "Only a STUDENT may book a mentorship session");

        MentorProfile mentor = mentorProfileRepository.findById(request.getMentorProfileId())
                .orElseThrow(() -> new CustomException("Mentor profile not found", HttpStatus.NOT_FOUND));

        if (Boolean.FALSE.equals(mentor.getIsAvailable())) {
            throw new CustomException("This mentor is not currently available for sessions",
                    HttpStatus.BAD_REQUEST);
        }

        if (sessionRepository.existsByStudentIdAndMentorProfileIdAndStatusIn(
                studentId, mentor.getId(), ACTIVE_SESSION_STATUSES)) {
            throw new CustomException("You already have an active session with this mentor",
                    HttpStatus.CONFLICT);
        }

        MentorshipSession session = sessionRepository.save(MentorshipSession.builder()
                .studentId(studentId)
                .mentorUserId(mentor.getUserId())
                .mentorProfile(mentor)
                .topic(request.getTopic())
                .scheduledAt(request.getScheduledAt())
                .durationMinutes(request.getDurationMinutes() == null
                        ? DEFAULT_DURATION_MINUTES
                        : request.getDurationMinutes())
                .status(MentorshipSession.STATUS_REQUESTED)
                .build());

        eventPublisher.publishSessionBooked(SessionBookedEvent.builder()
                .sessionId(session.getId())
                .studentId(studentId)
                .mentorUserId(mentor.getUserId())
                .mentorFirstName(mentor.getFirstName())
                .mentorLastName(mentor.getLastName())
                .topic(session.getTopic())
                .scheduledAt(session.getScheduledAt())
                .build());

        log.info("Session {} booked by studentId={} with mentorUserId={}",
                session.getId(), studentId, mentor.getUserId());
        return toSessionResponse(session);
    }

    @Override
    @Transactional
    public MentorshipSessionResponse respondToSession(Long mentorUserId, String role, Long sessionId,
                                                      RespondToSessionRequest request) {
        requireRole(role, ROLE_MENTOR, "Only a MENTOR may respond to a session request");

        MentorshipSession session = requireSessionOwnedByMentor(sessionId, mentorUserId);

        if (!MentorshipSession.STATUS_REQUESTED.equals(session.getStatus())) {
            throw new CustomException("Session is not in REQUESTED status", HttpStatus.BAD_REQUEST);
        }

        String action = request.getAction() == null ? "" : request.getAction().trim().toUpperCase();

        if (ACTION_ACCEPT.equals(action)) {
            if (!hasText(request.getMeetingLink())) {
                throw new CustomException("Meeting link is required when accepting a session",
                        HttpStatus.BAD_REQUEST);
            }
            session.setStatus(MentorshipSession.STATUS_ACCEPTED);
            session.setMeetingLink(request.getMeetingLink());
            session.setMentorNotes(request.getMentorNotes());

            MentorshipSession saved = sessionRepository.save(session);

            eventPublisher.publishSessionAccepted(SessionAcceptedEvent.builder()
                    .sessionId(saved.getId())
                    .studentId(saved.getStudentId())
                    .mentorUserId(mentorUserId)
                    .mentorFirstName(saved.getMentorProfile().getFirstName())
                    .topic(saved.getTopic())
                    .scheduledAt(saved.getScheduledAt())
                    .meetingLink(saved.getMeetingLink())
                    .build());

            log.info("Session {} accepted by mentorUserId={}", saved.getId(), mentorUserId);
            return toSessionResponse(saved);
        }

        if (ACTION_DECLINE.equals(action)) {
            session.setStatus(MentorshipSession.STATUS_DECLINED);
            session.setMentorNotes(request.getMentorNotes());

            // No event on decline -- see SessionAcceptedEvent for why there is no session.declined.
            MentorshipSession saved = sessionRepository.save(session);
            log.info("Session {} declined by mentorUserId={}", saved.getId(), mentorUserId);
            return toSessionResponse(saved);
        }

        throw new CustomException("Action must be ACCEPT or DECLINE", HttpStatus.BAD_REQUEST);
    }

    @Override
    @Transactional
    public MentorshipSessionResponse completeSession(Long mentorUserId, String role, Long sessionId) {
        requireRole(role, ROLE_MENTOR, "Only a MENTOR may complete a session");

        MentorshipSession session = requireSessionOwnedByMentor(sessionId, mentorUserId);

        if (!MentorshipSession.STATUS_ACCEPTED.equals(session.getStatus())) {
            throw new CustomException("Only ACCEPTED sessions can be marked as completed",
                    HttpStatus.BAD_REQUEST);
        }

        session.setStatus(MentorshipSession.STATUS_COMPLETED);
        MentorshipSession saved = sessionRepository.save(session);

        MentorProfile profile = saved.getMentorProfile();
        profile.setSessionsCompleted(nullSafe(profile.getSessionsCompleted()) + 1);
        mentorProfileRepository.save(profile);

        // Counted here, in the service that owns the rows, and published as an absolute total so
        // prs-service can SET rather than accumulate. Counting after the save above is deliberate:
        // this session must be included in its own event.
        long completedForStudent = sessionRepository.countByStudentIdAndStatus(
                saved.getStudentId(), MentorshipSession.STATUS_COMPLETED);

        eventPublisher.publishSessionCompleted(SessionCompletedEvent.builder()
                .sessionId(saved.getId())
                .studentId(saved.getStudentId())
                .mentorUserId(mentorUserId)
                .mentorFirstName(profile.getFirstName())
                .topic(saved.getTopic())
                .studentSessionsCompleted((int) completedForStudent)
                .build());

        log.info("Session {} completed by mentorUserId={}; studentId={} now has {} completed sessions",
                saved.getId(), mentorUserId, saved.getStudentId(), completedForStudent);
        return toSessionResponse(saved);
    }

    @Override
    @Transactional
    public MentorshipSessionResponse cancelSession(Long requesterId, String requesterRole, Long sessionId) {
        MentorshipSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException("Session not found", HttpStatus.NOT_FOUND));

        if (ROLE_STUDENT.equals(requesterRole)) {
            if (!Objects.equals(session.getStudentId(), requesterId)) {
                throw new CustomException("You may only cancel your own sessions", HttpStatus.FORBIDDEN);
            }
            // Narrower than the mentor's window on purpose: once a mentor has accepted and blocked
            // out the time, withdrawing is the mentor's call to make, not a silent student action.
            if (!MentorshipSession.STATUS_REQUESTED.equals(session.getStatus())) {
                throw new CustomException("Only pending sessions can be cancelled by the student",
                        HttpStatus.BAD_REQUEST);
            }
        } else if (ROLE_MENTOR.equals(requesterRole)) {
            if (!Objects.equals(session.getMentorUserId(), requesterId)) {
                throw new CustomException("You may only cancel your own sessions", HttpStatus.FORBIDDEN);
            }
            if (!ACTIVE_SESSION_STATUSES.contains(session.getStatus())) {
                throw new CustomException("Session cannot be cancelled at this stage",
                        HttpStatus.BAD_REQUEST);
            }
        } else {
            throw new CustomException("Only the student or the mentor may cancel a session",
                    HttpStatus.FORBIDDEN);
        }

        session.setStatus(MentorshipSession.STATUS_CANCELLED);
        MentorshipSession saved = sessionRepository.save(session);

        log.info("Session {} cancelled by {} userId={}", saved.getId(), requesterRole, requesterId);
        return toSessionResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MentorshipSessionResponse> getMySessionsAsStudent(Long studentId, String role) {
        requireRole(role, ROLE_STUDENT, "Only a STUDENT has student sessions");
        return sessionRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
                .stream().map(this::toSessionResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MentorshipSessionResponse> getMySessionsAsMentor(Long mentorUserId, String role) {
        requireRole(role, ROLE_MENTOR, "Only a MENTOR has mentor sessions");
        return sessionRepository.findByMentorUserIdOrderByCreatedAtDesc(mentorUserId)
                .stream().map(this::toSessionResponse).toList();
    }

    // ---------------------------------------------------------------------------------------------
    // Reviews
    // ---------------------------------------------------------------------------------------------

    @Override
    @Transactional
    public SessionReviewResponse createReview(Long studentId, String role, Long sessionId,
                                              CreateReviewRequest request) {
        requireRole(role, ROLE_STUDENT, "Only a STUDENT may review a session");

        MentorshipSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException("Session not found", HttpStatus.NOT_FOUND));

        // 403 rather than 404: the student addressed a real session, and saying "not found" for a
        // row that plainly exists would be misleading.
        if (!Objects.equals(session.getStudentId(), studentId)) {
            throw new CustomException("You may only review your own sessions", HttpStatus.FORBIDDEN);
        }

        if (!MentorshipSession.STATUS_COMPLETED.equals(session.getStatus())) {
            throw new CustomException("Reviews can only be left for completed sessions",
                    HttpStatus.BAD_REQUEST);
        }

        // Fast path; uq_review_session is the real guarantee.
        if (reviewRepository.existsBySessionId(sessionId)) {
            throw new CustomException("You have already reviewed this session", HttpStatus.CONFLICT);
        }

        MentorProfile profile = session.getMentorProfile();

        SessionReview review = reviewRepository.save(SessionReview.builder()
                .session(session)
                .studentId(studentId)
                .mentorProfileId(profile.getId())
                .rating(request.getRating())
                .comment(request.getComment())
                .build());

        recalculateAverageRating(profile);

        log.info("Review {} created for session {} (rating={})",
                review.getId(), sessionId, review.getRating());
        return toReviewResponse(review, sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionReviewResponse> getReviewsForMentor(Long mentorProfileId) {
        return reviewRepository.findByMentorProfileIdOrderByCreatedAtDesc(mentorProfileId)
                .stream()
                // The session id is read off the denormalised FK rather than the LAZY association,
                // so this list never triggers a proxy load per row.
                .map(review -> toReviewResponse(review, null))
                .toList();
    }

    /**
     * Recomputed from every review the mentor has, never maintained as a running average.
     *
     * An incremental ((old * n) + new) / (n + 1) drifts with floating point and is unrecoverable
     * once wrong; a full recount is a single indexed query on a column that is already denormalised
     * for exactly this. Same "recompute, never increment" rule as prs-service's totalScore and
     * roadmap-service's completedMilestones.
     */
    private void recalculateAverageRating(MentorProfile profile) {
        List<SessionReview> all = reviewRepository.findByMentorProfileIdOrderByCreatedAtDesc(profile.getId());

        double average = all.stream()
                .map(SessionReview::getRating)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        profile.setAverageRating(BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
        mentorProfileRepository.save(profile);
    }

    // ---------------------------------------------------------------------------------------------
    // Guards and mapping
    // ---------------------------------------------------------------------------------------------

    private void requireRole(String actual, String expected, String message) {
        if (!expected.equals(actual)) {
            throw new CustomException(message, HttpStatus.FORBIDDEN);
        }
    }

    private MentorProfile requireOwnProfile(Long userId) {
        return mentorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(
                        "You do not have a mentor profile yet", HttpStatus.NOT_FOUND));
    }

    /**
     * 404 when no such session exists, 403 when it belongs to a different mentor -- deliberately
     * NOT a findByIdAndMentorUserId compound finder, which would collapse both into one empty
     * Optional and lose the distinction the caller is owed. Same choice roadmap-service made for
     * completeMilestone.
     */
    private MentorshipSession requireSessionOwnedByMentor(Long sessionId, Long mentorUserId) {
        MentorshipSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException("Session not found", HttpStatus.NOT_FOUND));

        if (!Objects.equals(session.getMentorUserId(), mentorUserId)) {
            throw new CustomException("This session belongs to another mentor", HttpStatus.FORBIDDEN);
        }
        return session;
    }

    private MentorProfileResponse toProfileResponse(MentorProfile profile) {
        return MentorProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .bio(profile.getBio())
                .currentCompany(profile.getCurrentCompany())
                .currentRole(profile.getCurrentRole())
                .yearsOfExperience(profile.getYearsOfExperience())
                .expertiseAreas(splitCsv(profile.getExpertiseAreas()))
                .careerPaths(splitCsv(profile.getCareerPaths()))
                .linkedinUrl(profile.getLinkedinUrl())
                .isAvailable(profile.getIsAvailable())
                .sessionsCompleted(profile.getSessionsCompleted())
                .averageRating(profile.getAverageRating())
                .createdAt(profile.getCreatedAt())
                .build();
    }

    /**
     * Touches session.getMentorProfile(), which is LAZY -- every caller of this method is inside a
     * @Transactional boundary so the proxy can initialise. Mapping outside one throws
     * LazyInitializationException.
     */
    private MentorshipSessionResponse toSessionResponse(MentorshipSession session) {
        return MentorshipSessionResponse.builder()
                .id(session.getId())
                .studentId(session.getStudentId())
                .mentorUserId(session.getMentorUserId())
                .mentorProfile(toProfileResponse(session.getMentorProfile()))
                .topic(session.getTopic())
                .scheduledAt(session.getScheduledAt())
                .durationMinutes(session.getDurationMinutes())
                .status(session.getStatus())
                .meetingLink(session.getMeetingLink())
                .mentorNotes(session.getMentorNotes())
                .createdAt(session.getCreatedAt())
                .build();
    }

    /**
     * @param sessionId passed in rather than read from review.getSession().getId(), so the public
     *                  reviews list does not initialise one LAZY session proxy per row. Null when
     *                  the caller does not have it to hand.
     */
    private SessionReviewResponse toReviewResponse(SessionReview review, Long sessionId) {
        return SessionReviewResponse.builder()
                .id(review.getId())
                .sessionId(sessionId)
                .studentId(review.getStudentId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }

    /** "Java, Spring Boot ,, System Design" becomes three trimmed entries, never a blank one. */
    private List<String> splitCsv(String csv) {
        if (!hasText(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }
}
