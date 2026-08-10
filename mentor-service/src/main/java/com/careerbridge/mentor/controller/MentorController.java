package com.careerbridge.mentor.controller;

import com.careerbridge.mentor.dto.BookSessionRequest;
import com.careerbridge.mentor.dto.CreateMentorProfileRequest;
import com.careerbridge.mentor.dto.CreateReviewRequest;
import com.careerbridge.mentor.dto.MentorProfileResponse;
import com.careerbridge.mentor.dto.MentorshipSessionResponse;
import com.careerbridge.mentor.dto.RespondToSessionRequest;
import com.careerbridge.mentor.dto.SessionReviewResponse;
import com.careerbridge.mentor.dto.UpdateMentorProfileRequest;
import com.careerbridge.mentor.service.MentorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * /api/mentor -- singular, matching the gateway's Path=/api/mentor/** predicate. Gateway predicates
 * match whole segments, so a plural mapping would 404 every request arriving through it.
 *
 * This class contains no authorization logic whatsoever. It reads the gateway-injected identity
 * headers and hands them to the service, which is the only place that decides anything -- see
 * MentorServiceImpl.
 *
 * X-User-Org-Id is required = false on every method, without exception: SUPER_ADMIN and RECRUITER
 * belong to no organization, so the gateway sends no header at all for them, and a required binding
 * would 400 the request before the service is ever reached.
 */
@RestController
@RequestMapping("/api/mentor")
public class MentorController {

    private static final String H_ID = "X-User-Id";
    private static final String H_ROLE = "X-User-Role";
    private static final String H_ORG = "X-User-Org-Id";

    private final MentorService mentorService;

    public MentorController(MentorService mentorService) {
        this.mentorService = mentorService;
    }

    // -- Mentor profile ---------------------------------------------------------------------------

    @PostMapping("/profile")
    public ResponseEntity<MentorProfileResponse> createProfile(
            @RequestHeader(H_ID) Long userId,
            @RequestHeader(H_ROLE) String role,
            @RequestHeader(value = H_ORG, required = false) Long orgId,
            @RequestBody @Valid CreateMentorProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mentorService.createProfile(userId, role, request));
    }

    @PutMapping("/profile")
    public ResponseEntity<MentorProfileResponse> updateProfile(
            @RequestHeader(H_ID) Long userId,
            @RequestHeader(H_ROLE) String role,
            @RequestHeader(value = H_ORG, required = false) Long orgId,
            @RequestBody @Valid UpdateMentorProfileRequest request) {
        return ResponseEntity.ok(mentorService.updateProfile(userId, role, request));
    }

    @GetMapping("/profile/my")
    public ResponseEntity<MentorProfileResponse> getMyProfile(
            @RequestHeader(H_ID) Long userId,
            @RequestHeader(H_ROLE) String role,
            @RequestHeader(value = H_ORG, required = false) Long orgId) {
        return ResponseEntity.ok(mentorService.getMyProfile(userId, role));
    }

    /** Public within the platform -- any authenticated user may view a mentor's profile. */
    @GetMapping("/profile/{id}")
    public ResponseEntity<MentorProfileResponse> getProfileById(
            @PathVariable Long id,
            @RequestHeader(H_ROLE) String role,
            @RequestHeader(value = H_ORG, required = false) Long orgId) {
        return ResponseEntity.ok(mentorService.getProfileById(id));
    }

    /** careerPath and expertise are independent optional filters; careerPath wins if both are sent. */
    @GetMapping("/browse")
    public ResponseEntity<List<MentorProfileResponse>> browseMentors(
            @RequestParam(required = false) String careerPath,
            @RequestParam(required = false) String expertise,
            @RequestHeader(H_ROLE) String role,
            @RequestHeader(value = H_ORG, required = false) Long orgId) {
        return ResponseEntity.ok(mentorService.browseMentors(careerPath, expertise));
    }

    // -- Sessions ---------------------------------------------------------------------------------

    @PostMapping("/sessions")
    public ResponseEntity<MentorshipSessionResponse> bookSession(
            @RequestHeader(H_ID) Long userId,
            @RequestHeader(H_ROLE) String role,
            @RequestHeader(value = H_ORG, required = false) Long orgId,
            @RequestBody @Valid BookSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mentorService.bookSession(userId, role, request));
    }

    @PatchMapping("/sessions/{id}/respond")
    public ResponseEntity<MentorshipSessionResponse> respondToSession(
            @PathVariable Long id,
            @RequestHeader(H_ID) Long userId,
            @RequestHeader(H_ROLE) String role,
            @RequestHeader(value = H_ORG, required = false) Long orgId,
            @RequestBody @Valid RespondToSessionRequest request) {
        return ResponseEntity.ok(mentorService.respondToSession(userId, role, id, request));
    }

    @PatchMapping("/sessions/{id}/complete")
    public ResponseEntity<MentorshipSessionResponse> completeSession(
            @PathVariable Long id,
            @RequestHeader(H_ID) Long userId,
            @RequestHeader(H_ROLE) String role,
            @RequestHeader(value = H_ORG, required = false) Long orgId) {
        return ResponseEntity.ok(mentorService.completeSession(userId, role, id));
    }

    /** Either party may cancel; the service decides which windows each of them gets. */
    @PatchMapping("/sessions/{id}/cancel")
    public ResponseEntity<MentorshipSessionResponse> cancelSession(
            @PathVariable Long id,
            @RequestHeader(H_ID) Long userId,
            @RequestHeader(H_ROLE) String role,
            @RequestHeader(value = H_ORG, required = false) Long orgId) {
        return ResponseEntity.ok(mentorService.cancelSession(userId, role, id));
    }

    @GetMapping("/sessions/my/student")
    public ResponseEntity<List<MentorshipSessionResponse>> getMySessionsAsStudent(
            @RequestHeader(H_ID) Long userId,
            @RequestHeader(H_ROLE) String role,
            @RequestHeader(value = H_ORG, required = false) Long orgId) {
        return ResponseEntity.ok(mentorService.getMySessionsAsStudent(userId, role));
    }

    @GetMapping("/sessions/my/mentor")
    public ResponseEntity<List<MentorshipSessionResponse>> getMySessionsAsMentor(
            @RequestHeader(H_ID) Long userId,
            @RequestHeader(H_ROLE) String role,
            @RequestHeader(value = H_ORG, required = false) Long orgId) {
        return ResponseEntity.ok(mentorService.getMySessionsAsMentor(userId, role));
    }

    // -- Reviews ----------------------------------------------------------------------------------

    @PostMapping("/sessions/{id}/review")
    public ResponseEntity<SessionReviewResponse> createReview(
            @PathVariable Long id,
            @RequestHeader(H_ID) Long userId,
            @RequestHeader(H_ROLE) String role,
            @RequestHeader(value = H_ORG, required = false) Long orgId,
            @RequestBody @Valid CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mentorService.createReview(userId, role, id, request));
    }

    @GetMapping("/profile/{id}/reviews")
    public ResponseEntity<List<SessionReviewResponse>> getReviewsForMentor(
            @PathVariable Long id,
            @RequestHeader(H_ROLE) String role,
            @RequestHeader(value = H_ORG, required = false) Long orgId) {
        return ResponseEntity.ok(mentorService.getReviewsForMentor(id));
    }
}
