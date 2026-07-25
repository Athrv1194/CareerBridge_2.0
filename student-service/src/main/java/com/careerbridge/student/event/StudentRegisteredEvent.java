package com.careerbridge.student.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Consumer-side copy of the payload auth-service publishes on registration
 * (exchange careerbridge.exchange, routing key student.registered).
 *
 * Field names and JSON shape must stay in lockstep with
 * com.careerbridge.auth.event.StudentRegisteredEvent. The differing package is fine: the listener
 * method takes this concrete type, and Spring AMQP's default TypePrecedence.INFERRED resolves the
 * payload from the method signature without ever reading the sender's __TypeId__ header.
 *
 * role is a String, not an enum copy: wire-identical ("STUDENT"), but a duplicated enum would make
 * Jackson hard-fail every event the day auth-service adds a seventh role. This service does not
 * store the role anyway.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentRegisteredEvent {

    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private Long organizationId;
    private LocalDateTime registeredAt;
}
