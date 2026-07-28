package com.careerbridge.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Consumer-side copy of the payload auth-service publishes on registration
 * (exchange careerbridge.exchange, routing key student.registered).
 *
 * Consumed here purely to harvest contact details: RecommendationGeneratedEvent carries only a
 * userId, so without this there would be no address to email. Handled by upserting a UserContact
 * row, not by creating any notification -- registering is not something the student needs telling
 * about.
 *
 * role is a String, NOT a copy of auth-service's Role enum, even though the publisher declares it
 * as one. Wire-identical ("STUDENT"), but a duplicated enum would make Jackson hard-fail every
 * event the day auth-service adds a seventh role. student-service's copy does the same. This
 * service does not read the field at all -- it is present only to document the payload.
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
