package com.careerbridge.mentor.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Published on session.booked when a student requests a session.
 *
 * Consumed by notification-service on careerbridge.notification.session.booked.queue, which emails
 * the MENTOR. The mentor's name is carried on the event so that consumer does not need an HTTP call
 * back to this service to render a readable message.
 *
 * Consumers hold their own copy of this class in their own package; the field names and JSON shape
 * are the contract. Adding a field is backward compatible (Jackson 3 has FAIL_ON_UNKNOWN_PROPERTIES
 * off); renaming or removing one is not.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionBookedEvent {

    private Long sessionId;
    private Long studentId;
    private Long mentorUserId;
    private String mentorFirstName;
    private String mentorLastName;
    private String topic;
    private LocalDateTime scheduledAt;
}
