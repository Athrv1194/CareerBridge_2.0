package com.careerbridge.mentor.repository;

import com.careerbridge.mentor.model.SessionReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionReviewRepository extends JpaRepository<SessionReview, Long> {

    /** Optional is correct: uq_review_session makes session_id unique. */
    Optional<SessionReview> findBySessionId(Long sessionId);

    /** Reads the denormalised column, not a join through mentorship_sessions. */
    List<SessionReview> findByMentorProfileIdOrderByCreatedAtDesc(Long mentorProfileId);

    boolean existsBySessionId(Long sessionId);
}
