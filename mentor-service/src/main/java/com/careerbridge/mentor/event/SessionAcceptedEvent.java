package com.careerbridge.mentor.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Published on session.accepted when a mentor accepts a request.
 *
 * Consumed by notification-service, which emails the STUDENT. meetingLink is the payload that
 * matters: this event is how the student learns where the call is.
 *
 * Nothing is published on DECLINE. A decline is visible on the student's own session list, and an
 * email saying "you were turned down" is a product decision nobody has taken -- adding one later
 * means a session.declined key and its own queue, not a flag on this event.
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
