package com.careerbridge.prs.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Consumer-side copy of the payload mentor-service publishes when a mentor marks a session
 * COMPLETED (exchange careerbridge.exchange, routing key session.completed).
 *
 * Field names and JSON shape must stay in lockstep with
 * com.careerbridge.mentor.event.SessionCompletedEvent. The differing package is fine: the listener
 * method takes this concrete type, and Spring AMQP's default TypePrecedence.INFERRED resolves the
 * payload from the method signature without ever reading the sender's __TypeId__ header.
 *
 * studentSessionsCompleted is the field this service actually consumes, and it is an ABSOLUTE
 * running total rather than a delta -- how many completed sessions that student now has, counted in
 * mentor-service, which owns the rows. RabbitMQ is at-least-once, so a "+1" delta would silently
 * double-count on any redelivery; an absolute count is idempotent because the consumer SETS from it
 * rather than accumulating. Same rule and same reasoning as auth-service's subscription consumer,
 * which sets subscriptionExpiry from an absolute validUntil instead of adding thirty days.
 *
 * mentorFirstName and topic are unused here -- they exist for notification-service, which binds its
 * own queue to this same routing key. Jackson 3 has FAIL_ON_UNKNOWN_PROPERTIES off, so carrying
 * them costs nothing.
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
