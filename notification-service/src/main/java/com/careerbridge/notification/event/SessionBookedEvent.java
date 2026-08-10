package com.careerbridge.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Consumer-side copy of the payload mentor-service publishes when a student books a session
 * (exchange careerbridge.exchange, routing key session.booked).
 *
 * Field names and JSON shape must stay in lockstep with
 * com.careerbridge.mentor.event.SessionBookedEvent. The differing package is fine: the listener
 * method takes this concrete type, and Spring AMQP's default TypePrecedence.INFERRED resolves the
 * payload from the method signature rather than the sender's __TypeId__ header.
 *
 * The recipient of this notification is the MENTOR, not the student -- mentorUserId is the id this
 * service looks up in user_contacts.
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
