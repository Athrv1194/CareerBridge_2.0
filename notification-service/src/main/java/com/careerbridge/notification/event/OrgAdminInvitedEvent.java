package com.careerbridge.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A local copy of auth-service's event of the same name. No shared module in this project; every
 * consumer keeps its own copy, and Spring AMQP's default TypePrecedence.INFERRED resolves the
 * payload from the listener's parameter type, so the differing package FQN needs no type-mapper
 * configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgAdminInvitedEvent {

    private String email;
    private String firstName;
    private String organizationName;
    private String resetToken;
    private int expiresInHours;
}
