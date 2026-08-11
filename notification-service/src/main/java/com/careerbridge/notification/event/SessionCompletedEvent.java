package com.careerbridge.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Consumer-side copy of the payload mentor-service publishes when a mentor marks a session
 * COMPLETED (exchange careerbridge.exchange, routing key session.completed).
 *
 * Must stay in lockstep with com.careerbridge.mentor.event.SessionCompletedEvent. prs-service holds
 * a third copy of this same payload for its own queue on the same routing key.
 *
 * The recipient is the STUDENT, and the notification is a nudge to leave a review.
 * studentSessionsCompleted is carried for prs-service's benefit and is unused here; Jackson 3 has
 * FAIL_ON_UNKNOWN_PROPERTIES off, so it would be harmless to omit, but keeping the three copies
 * field-identical is what stops them drifting.
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
    private Integer studentSessionsCompleted;
}
