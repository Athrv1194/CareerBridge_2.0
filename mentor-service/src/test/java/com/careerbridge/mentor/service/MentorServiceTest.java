package com.careerbridge.mentor.service;

import com.careerbridge.mentor.dto.BookSessionRequest;
import com.careerbridge.mentor.dto.CreateMentorProfileRequest;
import com.careerbridge.mentor.dto.CreateReviewRequest;
import com.careerbridge.mentor.dto.MentorProfileResponse;
import com.careerbridge.mentor.dto.MentorshipSessionResponse;
import com.careerbridge.mentor.dto.RespondToSessionRequest;
import com.careerbridge.mentor.dto.UpdateMentorProfileRequest;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorServiceTest {

    private static final Long MENTOR_USER_ID = 10L;
    private static final Long STUDENT_ID = 20L;
    private static final Long OTHER_USER_ID = 99L;
    private static final Long PROFILE_ID = 1L;
    private static final Long SESSION_ID = 5L;
    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(7);

    @Mock
    private MentorProfileRepository mentorProfileRepository;
    @Mock
    private MentorshipSessionRepository sessionRepository;
    @Mock
    private SessionReviewRepository reviewRepository;
    @Mock
    private MentorEventPublisher eventPublisher;

    @InjectMocks
    private MentorServiceImpl mentorService;

    // -- fixtures ---------------------------------------------------------------------------------

    private MentorProfile profile() {
        return MentorProfile.builder()
                .id(PROFILE_ID)
                .userId(MENTOR_USER_ID)
                .firstName("Raj")
                .lastName("Sharma")
                .currentCompany("Infosys")
                .currentRole("Senior Software Engineer")
                .yearsOfExperience(5)
                .expertiseAreas("Java,Spring Boot,System Design")
                .careerPaths("Backend Developer,Full Stack Developer")
                .isAvailable(true)
                .sessionsCompleted(0)
                .averageRating(BigDecimal.ZERO)
                .build();
    }

    private MentorshipSession session(String status) {
        return MentorshipSession.builder()
                .id(SESSION_ID)
                .studentId(STUDENT_ID)
                .mentorUserId(MENTOR_USER_ID)
                .mentorProfile(profile())
                .topic("Java backend interviews")
                .scheduledAt(FUTURE)
                .durationMinutes(45)
                .status(status)
                .build();
    }

    private CreateMentorProfileRequest createRequest() {
        return CreateMentorProfileRequest.builder()
                .firstName("Raj").lastName("Sharma")
                .currentCompany("Infosys").currentRole("Senior Software Engineer")
                .yearsOfExperience(5)
                .expertiseAreas("Java,Spring Boot,System Design")
                .careerPaths("Backend Developer,Full Stack Developer")
                .build();
    }

    private BookSessionRequest bookRequest() {
        return BookSessionRequest.builder()
                .mentorProfileId(PROFILE_ID)
                .topic("Java backend interviews")
                .scheduledAt(FUTURE)
                .durationMinutes(45)
                .build();
    }

    private static CustomException expectFailure(Executable call) {
        return assertThrows(CustomException.class, call::run);
    }

    private interface Executable {
        void run();
    }

    // -- profile ----------------------------------------------------------------------------------

    @Test
    @DisplayName("a MENTOR can create a profile, and the comma strings come back as lists")
    void createProfile_Mentor_CreatesSuccessfully() {
        when(mentorProfileRepository.existsByUserId(MENTOR_USER_ID)).thenReturn(false);
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenAnswer(i -> i.getArgument(0));

        MentorProfileResponse response =
                mentorService.createProfile(MENTOR_USER_ID, "MENTOR", createRequest());

        assertEquals(List.of("Java", "Spring Boot", "System Design"), response.getExpertiseAreas());
        assertEquals(List.of("Backend Developer", "Full Stack Developer"), response.getCareerPaths());
        assertEquals(true, response.getIsAvailable());
        assertEquals(0, response.getSessionsCompleted());
    }

    @Test
    @DisplayName("a second profile for the same mentor is a 409")
    void createProfile_DuplicateProfile_409() {
        when(mentorProfileRepository.existsByUserId(MENTOR_USER_ID)).thenReturn(true);

        CustomException ex = expectFailure(
                () -> mentorService.createProfile(MENTOR_USER_ID, "MENTOR", createRequest()));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(mentorProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("a STUDENT cannot create a mentor profile, and the check runs before any repository call")
    void createProfile_StudentRole_403() {
        CustomException ex = expectFailure(
                () -> mentorService.createProfile(STUDENT_ID, "STUDENT", createRequest()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(mentorProfileRepository, never()).existsByUserId(anyLong());
    }

    @Test
    @DisplayName("update leaves unset fields untouched rather than clearing them")
    void updateProfile_NullFields_LeavesExistingValues() {
        MentorProfile existing = profile();
        existing.setBio("Original bio");
        when(mentorProfileRepository.findByUserId(MENTOR_USER_ID)).thenReturn(Optional.of(existing));
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenAnswer(i -> i.getArgument(0));

        mentorService.updateProfile(MENTOR_USER_ID, "MENTOR",
                UpdateMentorProfileRequest.builder().isAvailable(false).build());

        ArgumentCaptor<MentorProfile> captor = ArgumentCaptor.forClass(MentorProfile.class);
        verify(mentorProfileRepository).save(captor.capture());
        assertEquals("Original bio", captor.getValue().getBio(), "a partial update must not clear the bio");
        assertEquals(false, captor.getValue().getIsAvailable());
    }

    @Test
    @DisplayName("updating before creating a profile is a 404")
    void updateProfile_NoProfile_404() {
        when(mentorProfileRepository.findByUserId(MENTOR_USER_ID)).thenReturn(Optional.empty());

        CustomException ex = expectFailure(() -> mentorService.updateProfile(MENTOR_USER_ID, "MENTOR",
                UpdateMentorProfileRequest.builder().bio("x").build()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    @DisplayName("browse with no filter returns every available mentor, best rated first")
    void browseMentors_NoFilter_ReturnsAllAvailable() {
        when(mentorProfileRepository.findByIsAvailableTrueOrderByAverageRatingDesc())
                .thenReturn(List.of(profile()));

        assertEquals(1, mentorService.browseMentors(null, null).size());
        verify(mentorProfileRepository).findByIsAvailableTrueOrderByAverageRatingDesc();
    }

    @Test
    @DisplayName("browse by career path uses the career path finder")
    void browseMentors_ByCareerPath_FiltersCorrectly() {
        when(mentorProfileRepository.findByCareerPathsContainingIgnoreCaseAndIsAvailableTrue("Backend Developer"))
                .thenReturn(List.of(profile()));

        assertEquals(1, mentorService.browseMentors("Backend Developer", null).size());
        verify(mentorProfileRepository, never()).findByIsAvailableTrueOrderByAverageRatingDesc();
    }

    @Test
    @DisplayName("browse by expertise uses the expertise finder")
    void browseMentors_ByExpertise_FiltersCorrectly() {
        when(mentorProfileRepository.findByExpertiseAreasContainingIgnoreCaseAndIsAvailableTrue("Java"))
                .thenReturn(List.of(profile()));

        assertEquals(1, mentorService.browseMentors(null, "Java").size());
    }

    /** Pins the documented precedence, which is otherwise invisible from the signature. */
    @Test
    @DisplayName("when both filters are supplied, careerPath wins")
    void browseMentors_BothFilters_CareerPathWins() {
        when(mentorProfileRepository.findByCareerPathsContainingIgnoreCaseAndIsAvailableTrue("Backend Developer"))
                .thenReturn(List.of(profile()));

        mentorService.browseMentors("Backend Developer", "Java");

        verify(mentorProfileRepository, never())
                .findByExpertiseAreasContainingIgnoreCaseAndIsAvailableTrue(anyString());
    }

    @Test
    @DisplayName("a blank filter is treated as absent, not as a search for the empty string")
    void browseMentors_BlankFilter_TreatedAsNoFilter() {
        when(mentorProfileRepository.findByIsAvailableTrueOrderByAverageRatingDesc())
                .thenReturn(List.of(profile()));

        mentorService.browseMentors("   ", null);

        verify(mentorProfileRepository).findByIsAvailableTrueOrderByAverageRatingDesc();
    }

    // -- booking ----------------------------------------------------------------------------------

    @Test
    @DisplayName("a student booking an available mentor creates a REQUESTED session and publishes the event")
    void bookSession_Student_CreatesRequestedSessionAndPublishes() {
        when(mentorProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile()));
        when(sessionRepository.existsByStudentIdAndMentorProfileIdAndStatusIn(
                STUDENT_ID, PROFILE_ID, List.of("REQUESTED", "ACCEPTED"))).thenReturn(false);
        when(sessionRepository.save(any(MentorshipSession.class))).thenAnswer(i -> i.getArgument(0));

        MentorshipSessionResponse response =
                mentorService.bookSession(STUDENT_ID, "STUDENT", bookRequest());

        assertEquals("REQUESTED", response.getStatus());

        ArgumentCaptor<SessionBookedEvent> captor = ArgumentCaptor.forClass(SessionBookedEvent.class);
        verify(eventPublisher).publishSessionBooked(captor.capture());
        assertEquals(MENTOR_USER_ID, captor.getValue().getMentorUserId());
        assertEquals("Raj", captor.getValue().getMentorFirstName());
    }

    @Test
    @DisplayName("durationMinutes defaults to 30 when the request omits it")
    void bookSession_NoDuration_DefaultsTo30() {
        when(mentorProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile()));
        when(sessionRepository.existsByStudentIdAndMentorProfileIdAndStatusIn(anyLong(), anyLong(), anyList()))
                .thenReturn(false);
        when(sessionRepository.save(any(MentorshipSession.class))).thenAnswer(i -> i.getArgument(0));

        BookSessionRequest request = bookRequest();
        request.setDurationMinutes(null);

        assertEquals(30, mentorService.bookSession(STUDENT_ID, "STUDENT", request).getDurationMinutes());
    }

    @Test
    @DisplayName("booking an unavailable mentor is a 400")
    void bookSession_MentorUnavailable_400() {
        MentorProfile busy = profile();
        busy.setIsAvailable(false);
        when(mentorProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(busy));

        CustomException ex = expectFailure(
                () -> mentorService.bookSession(STUDENT_ID, "STUDENT", bookRequest()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("a second active session with the same mentor is a 409")
    void bookSession_DuplicateActiveSession_409() {
        when(mentorProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile()));
        when(sessionRepository.existsByStudentIdAndMentorProfileIdAndStatusIn(anyLong(), anyLong(), anyList()))
                .thenReturn(true);

        CustomException ex = expectFailure(
                () -> mentorService.bookSession(STUDENT_ID, "STUDENT", bookRequest()));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(eventPublisher, never()).publishSessionBooked(any());
    }

    /**
     * The duplicate-booking guard must check REQUESTED and ACCEPTED only. If COMPLETED ever creeps
     * into that list, a student could never book the same mentor a second time -- a silent
     * regression no other assertion here would catch.
     */
    @Test
    @DisplayName("the duplicate check covers REQUESTED and ACCEPTED only")
    void bookSession_DuplicateCheck_ExcludesTerminalStatuses() {
        when(mentorProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile()));
        when(sessionRepository.existsByStudentIdAndMentorProfileIdAndStatusIn(anyLong(), anyLong(), anyList()))
                .thenReturn(false);
        when(sessionRepository.save(any(MentorshipSession.class))).thenAnswer(i -> i.getArgument(0));

        mentorService.bookSession(STUDENT_ID, "STUDENT", bookRequest());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(sessionRepository).existsByStudentIdAndMentorProfileIdAndStatusIn(
                anyLong(), anyLong(), captor.capture());
        assertEquals(List.of("REQUESTED", "ACCEPTED"), captor.getValue());
    }

    @Test
    @DisplayName("a MENTOR cannot book a session")
    void bookSession_MentorRole_403() {
        CustomException ex = expectFailure(
                () -> mentorService.bookSession(MENTOR_USER_ID, "MENTOR", bookRequest()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    @DisplayName("booking a mentor profile that does not exist is a 404")
    void bookSession_UnknownMentor_404() {
        when(mentorProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, expectFailure(
                () -> mentorService.bookSession(STUDENT_ID, "STUDENT", bookRequest())).getStatus());
    }

    // -- respond ----------------------------------------------------------------------------------

    @Test
    @DisplayName("accepting sets ACCEPTED, stores the link and publishes the event carrying it")
    void respondToSession_Accept_SetsAcceptedAndPublishesEvent() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("REQUESTED")));
        when(sessionRepository.save(any(MentorshipSession.class))).thenAnswer(i -> i.getArgument(0));

        MentorshipSessionResponse response = mentorService.respondToSession(MENTOR_USER_ID, "MENTOR",
                SESSION_ID, RespondToSessionRequest.builder()
                        .action("ACCEPT").meetingLink("https://meet.google.com/abc").build());

        assertEquals("ACCEPTED", response.getStatus());
        assertEquals("https://meet.google.com/abc", response.getMeetingLink());
        verify(eventPublisher).publishSessionAccepted(any());
    }

    @Test
    @DisplayName("the action is case-insensitive")
    void respondToSession_LowercaseAction_Accepted() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("REQUESTED")));
        when(sessionRepository.save(any(MentorshipSession.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("ACCEPTED", mentorService.respondToSession(MENTOR_USER_ID, "MENTOR", SESSION_ID,
                RespondToSessionRequest.builder().action("accept").meetingLink("https://x").build())
                .getStatus());
    }

    @Test
    @DisplayName("accepting without a meeting link is a 400 and changes nothing")
    void respondToSession_Accept_MissingMeetingLink_400() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("REQUESTED")));

        CustomException ex = expectFailure(() -> mentorService.respondToSession(MENTOR_USER_ID, "MENTOR",
                SESSION_ID, RespondToSessionRequest.builder().action("ACCEPT").build()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(sessionRepository, never()).save(any());
        verify(eventPublisher, never()).publishSessionAccepted(any());
    }

    @Test
    @DisplayName("declining sets DECLINED and publishes nothing")
    void respondToSession_Decline_SetsDeclinedAndPublishesNothing() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("REQUESTED")));
        when(sessionRepository.save(any(MentorshipSession.class))).thenAnswer(i -> i.getArgument(0));

        MentorshipSessionResponse response = mentorService.respondToSession(MENTOR_USER_ID, "MENTOR",
                SESSION_ID, RespondToSessionRequest.builder()
                        .action("DECLINE").mentorNotes("Fully booked").build());

        assertEquals("DECLINED", response.getStatus());
        assertEquals("Fully booked", response.getMentorNotes());
        verify(eventPublisher, never()).publishSessionAccepted(any());
    }

    @Test
    @DisplayName("another mentor's session is a 403, not a 404 -- the row is real")
    void respondToSession_WrongMentor_403() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("REQUESTED")));

        CustomException ex = expectFailure(() -> mentorService.respondToSession(OTHER_USER_ID, "MENTOR",
                SESSION_ID, RespondToSessionRequest.builder().action("DECLINE").build()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    @DisplayName("responding to an already-accepted session is a 400")
    void respondToSession_SessionNotRequested_400() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("ACCEPTED")));

        CustomException ex = expectFailure(() -> mentorService.respondToSession(MENTOR_USER_ID, "MENTOR",
                SESSION_ID, RespondToSessionRequest.builder().action("ACCEPT").meetingLink("x").build()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    @DisplayName("an unrecognised action is a 400")
    void respondToSession_InvalidAction_400() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("REQUESTED")));

        CustomException ex = expectFailure(() -> mentorService.respondToSession(MENTOR_USER_ID, "MENTOR",
                SESSION_ID, RespondToSessionRequest.builder().action("MAYBE").build()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(sessionRepository, never()).save(any());
    }

    // -- complete ---------------------------------------------------------------------------------

    @Test
    @DisplayName("completing an accepted session marks it COMPLETED and increments the mentor's counter")
    void completeSession_AcceptedSession_SetsCompletedAndUpdatesMentorStats() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("ACCEPTED")));
        when(sessionRepository.save(any(MentorshipSession.class))).thenAnswer(i -> i.getArgument(0));
        when(sessionRepository.countByStudentIdAndStatus(STUDENT_ID, "COMPLETED")).thenReturn(1L);
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("COMPLETED",
                mentorService.completeSession(MENTOR_USER_ID, "MENTOR", SESSION_ID).getStatus());

        ArgumentCaptor<MentorProfile> captor = ArgumentCaptor.forClass(MentorProfile.class);
        verify(mentorProfileRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getSessionsCompleted());
    }

    /**
     * The event must carry an ABSOLUTE count, not a delta -- prs-service SETS its mentoring score
     * from this, and a delta would double-count on a RabbitMQ redelivery. The count is taken after
     * the status flip so this session is included in its own event.
     */
    @Test
    @DisplayName("session.completed carries the student's absolute completed-session count")
    void completeSession_PublishesAbsoluteSessionCount() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("ACCEPTED")));
        when(sessionRepository.save(any(MentorshipSession.class))).thenAnswer(i -> i.getArgument(0));
        when(sessionRepository.countByStudentIdAndStatus(STUDENT_ID, "COMPLETED")).thenReturn(4L);
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenAnswer(i -> i.getArgument(0));

        mentorService.completeSession(MENTOR_USER_ID, "MENTOR", SESSION_ID);

        ArgumentCaptor<SessionCompletedEvent> captor =
                ArgumentCaptor.forClass(SessionCompletedEvent.class);
        verify(eventPublisher).publishSessionCompleted(captor.capture());
        assertEquals(4, captor.getValue().getStudentSessionsCompleted());
        assertEquals(STUDENT_ID, captor.getValue().getStudentId());
    }

    @Test
    @DisplayName("only an ACCEPTED session can be completed")
    void completeSession_NotAccepted_400() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("REQUESTED")));

        assertEquals(HttpStatus.BAD_REQUEST,
                expectFailure(() -> mentorService.completeSession(MENTOR_USER_ID, "MENTOR", SESSION_ID))
                        .getStatus());
    }

    @Test
    @DisplayName("another mentor cannot complete a session")
    void completeSession_WrongMentor_403() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("ACCEPTED")));

        assertEquals(HttpStatus.FORBIDDEN,
                expectFailure(() -> mentorService.completeSession(OTHER_USER_ID, "MENTOR", SESSION_ID))
                        .getStatus());
    }

    // -- cancel -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a student may cancel their own REQUESTED session")
    void cancelSession_StudentCancelsOwnRequested_OK() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("REQUESTED")));
        when(sessionRepository.save(any(MentorshipSession.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("CANCELLED",
                mentorService.cancelSession(STUDENT_ID, "STUDENT", SESSION_ID).getStatus());
    }

    /** The asymmetry that makes the two roles different: once accepted, it is the mentor's call. */
    @Test
    @DisplayName("a student may NOT cancel once the mentor has accepted")
    void cancelSession_StudentCancelsAfterAccepted_400() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("ACCEPTED")));

        assertEquals(HttpStatus.BAD_REQUEST,
                expectFailure(() -> mentorService.cancelSession(STUDENT_ID, "STUDENT", SESSION_ID))
                        .getStatus());
    }

    @Test
    @DisplayName("a mentor may cancel an ACCEPTED session")
    void cancelSession_MentorCancelsAccepted_OK() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("ACCEPTED")));
        when(sessionRepository.save(any(MentorshipSession.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("CANCELLED",
                mentorService.cancelSession(MENTOR_USER_ID, "MENTOR", SESSION_ID).getStatus());
    }

    @Test
    @DisplayName("cancelling someone else's session is a 403")
    void cancelSession_WrongOwner_403() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("REQUESTED")));

        assertEquals(HttpStatus.FORBIDDEN,
                expectFailure(() -> mentorService.cancelSession(OTHER_USER_ID, "STUDENT", SESSION_ID))
                        .getStatus());
    }

    @Test
    @DisplayName("a completed session cannot be cancelled by the mentor either")
    void cancelSession_MentorCancelsCompleted_400() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("COMPLETED")));

        assertEquals(HttpStatus.BAD_REQUEST,
                expectFailure(() -> mentorService.cancelSession(MENTOR_USER_ID, "MENTOR", SESSION_ID))
                        .getStatus());
    }

    @Test
    @DisplayName("a role that is neither student nor mentor cannot cancel")
    void cancelSession_OtherRole_403() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("REQUESTED")));

        assertEquals(HttpStatus.FORBIDDEN,
                expectFailure(() -> mentorService.cancelSession(OTHER_USER_ID, "ORG_ADMIN", SESSION_ID))
                        .getStatus());
    }

    // -- reviews ----------------------------------------------------------------------------------

    @Test
    @DisplayName("reviewing a completed session stores it and recalculates the mentor's average")
    void createReview_CompletedSession_CreatesReviewAndUpdatesAverage() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("COMPLETED")));
        when(reviewRepository.existsBySessionId(SESSION_ID)).thenReturn(false);
        when(reviewRepository.save(any(SessionReview.class))).thenAnswer(i -> i.getArgument(0));
        when(reviewRepository.findByMentorProfileIdOrderByCreatedAtDesc(PROFILE_ID))
                .thenReturn(List.of(review(5), review(4)));
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(5, mentorService.createReview(STUDENT_ID, "STUDENT", SESSION_ID,
                CreateReviewRequest.builder().rating(5).comment("Very helpful").build()).getRating());

        ArgumentCaptor<MentorProfile> captor = ArgumentCaptor.forClass(MentorProfile.class);
        verify(mentorProfileRepository).save(captor.capture());
        assertEquals(new BigDecimal("4.50"), captor.getValue().getAverageRating());
    }

    /**
     * The average is recomputed from every row, never nudged. 5, 4, 4 rounds HALF_UP to 4.33 -- a
     * running-average implementation drifts here, and the two-decimal scale is a stored contract.
     */
    @Test
    @DisplayName("the average is a HALF_UP mean of every review, to two decimals")
    void createReview_AverageRatingRecalculatedCorrectly() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("COMPLETED")));
        when(reviewRepository.existsBySessionId(SESSION_ID)).thenReturn(false);
        when(reviewRepository.save(any(SessionReview.class))).thenAnswer(i -> i.getArgument(0));
        when(reviewRepository.findByMentorProfileIdOrderByCreatedAtDesc(PROFILE_ID))
                .thenReturn(List.of(review(5), review(4), review(4)));
        when(mentorProfileRepository.save(any(MentorProfile.class))).thenAnswer(i -> i.getArgument(0));

        mentorService.createReview(STUDENT_ID, "STUDENT", SESSION_ID,
                CreateReviewRequest.builder().rating(4).build());

        ArgumentCaptor<MentorProfile> captor = ArgumentCaptor.forClass(MentorProfile.class);
        verify(mentorProfileRepository).save(captor.capture());
        assertEquals(new BigDecimal("4.33"), captor.getValue().getAverageRating());
    }

    @Test
    @DisplayName("reviewing a session that is not COMPLETED is a 400")
    void createReview_SessionNotCompleted_400() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("ACCEPTED")));

        assertEquals(HttpStatus.BAD_REQUEST, expectFailure(() -> mentorService.createReview(
                STUDENT_ID, "STUDENT", SESSION_ID,
                CreateReviewRequest.builder().rating(5).build())).getStatus());
    }

    @Test
    @DisplayName("a second review for the same session is a 409")
    void createReview_DuplicateReview_409() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("COMPLETED")));
        when(reviewRepository.existsBySessionId(SESSION_ID)).thenReturn(true);

        assertEquals(HttpStatus.CONFLICT, expectFailure(() -> mentorService.createReview(
                STUDENT_ID, "STUDENT", SESSION_ID,
                CreateReviewRequest.builder().rating(3).build())).getStatus());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("reviewing someone else's session is a 403")
    void createReview_WrongStudent_403() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session("COMPLETED")));

        assertEquals(HttpStatus.FORBIDDEN, expectFailure(() -> mentorService.createReview(
                OTHER_USER_ID, "STUDENT", SESSION_ID,
                CreateReviewRequest.builder().rating(5).build())).getStatus());
    }

    @Test
    @DisplayName("a MENTOR cannot review a session")
    void createReview_MentorRole_403() {
        assertEquals(HttpStatus.FORBIDDEN, expectFailure(() -> mentorService.createReview(
                MENTOR_USER_ID, "MENTOR", SESSION_ID,
                CreateReviewRequest.builder().rating(5).build())).getStatus());
    }

    @Test
    @DisplayName("the public review list comes back newest first")
    void getReviewsForMentor_ReturnsOrderedList() {
        when(reviewRepository.findByMentorProfileIdOrderByCreatedAtDesc(PROFILE_ID))
                .thenReturn(List.of(review(5), review(3)));

        assertEquals(2, mentorService.getReviewsForMentor(PROFILE_ID).size());
        assertTrue(mentorService.getReviewsForMentor(PROFILE_ID).get(0).getRating() == 5);
    }

    private SessionReview review(int rating) {
        return SessionReview.builder()
                .id((long) rating)
                .studentId(STUDENT_ID)
                .mentorProfileId(PROFILE_ID)
                .rating(rating)
                .build();
    }
}
