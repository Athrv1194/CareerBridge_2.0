package com.careerbridge.mentor.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published on session.completed when a mentor marks a session done.
 *
 * TWO independent consumers, each on its own queue: notification-service
 * (careerbridge.notification.session.completed.queue) nudges the student for a review, and
 * prs-service (careerbridge.prs.mentor.queue) updates their mentoring score. Both receive every
 * copy, since each queue is bound separately to the topic exchange.
 *
 * studentSessionsCompleted is an ABSOLUTE running total, deliberately -- how many sessions this
 * student has now completed across all mentors, counted here because this service owns the rows.
 * prs-service SETS min(100, count x 5) from it rather than adding five points per delivery.
 * RabbitMQ is at-least-once, so a delta would silently double-count a redelivery with nothing
 * anywhere to detect it; the same reasoning made payment-service's subscription event carry an
 * absolute validUntil instead of a duration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionCompletedEvent {

    private Long sessionId;
    private Long studentId;
    private Long mentorUserId;
    private String mentorFirstName;
    private String topic;

    /** Absolute count of this student's COMPLETED sessions, never a delta. */
    private Integer studentSessionsCompleted;
}
