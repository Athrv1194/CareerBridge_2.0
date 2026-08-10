package com.careerbridge.mentor.repository;

import com.careerbridge.mentor.model.MentorshipSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MentorshipSessionRepository extends JpaRepository<MentorshipSession, Long> {

    List<MentorshipSession> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<MentorshipSession> findByMentorUserIdOrderByCreatedAtDesc(Long mentorUserId);

    List<MentorshipSession> findByMentorUserIdAndStatusOrderByScheduledAtAsc(Long mentorUserId, String status);

    List<MentorshipSession> findByStudentIdAndStatusOrderByScheduledAtAsc(Long studentId, String status);

    /**
     * The one-active-session-per-(student, mentor) guard. mentorProfileId traverses the @ManyToOne
     * to mentorProfile.id.
     *
     * statuses is always [REQUESTED, ACCEPTED]: a COMPLETED, DECLINED or CANCELLED session must NOT
     * block a fresh booking with the same mentor, which is the whole reason this is a status-scoped
     * check rather than a unique constraint on the pair.
     */
    boolean existsByStudentIdAndMentorProfileIdAndStatusIn(Long studentId, Long mentorProfileId,
                                                           List<String> statuses);

    /**
     * How many sessions this student has completed, in total, across all mentors. Published on
     * session.completed as an absolute figure so prs-service can SET its mentoring score from it
     * instead of incrementing -- RabbitMQ is at-least-once and an accumulating consumer would
     * double-count a redelivery.
     */
    long countByStudentIdAndStatus(Long studentId, String status);
}
