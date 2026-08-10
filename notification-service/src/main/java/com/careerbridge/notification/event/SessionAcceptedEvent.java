package com.careerbridge.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Consumer-side copy of the payload mentor-service publishes when a mentor accepts a session
 * (exchange careerbridge.exchange, routing key session.accepted).
 *
 * Must stay in lockstep with com.careerbridge.mentor.event.SessionAcceptedEvent.
 *
 * The recipient here is the STUDENT -- the opposite direction from SessionBookedEvent -- and
 * meetingLink is the whole point of the notification: it is the only place the student is given the
 * call URL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionAcceptedEvent {

    private Long sessionId;
    private Long studentId;
    private Long mentorUserId;
    private String mentorFirstName;
    private String topic;
    private LocalDateTime scheduledAt;
    private String meetingLink;
}
