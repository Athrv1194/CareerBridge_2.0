package com.careerbridge.auth.event;

import com.careerbridge.auth.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Payload published to RabbitMQ on successful registration.
 * Consumed by notification-service; this class is the contract, so changes here
 * must stay backward compatible with that consumer.
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
    private Role role;
    private Long organizationId;
    private LocalDateTime registeredAt;
}
