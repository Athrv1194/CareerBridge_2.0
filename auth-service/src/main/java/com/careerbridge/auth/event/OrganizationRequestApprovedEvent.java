package com.careerbridge.auth.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A local copy of organization-service's event of the same name. No shared module in this project;
 * every consumer keeps its own copy, and Spring AMQP's default TypePrecedence.INFERRED resolves the
 * payload from this listener's parameter type, so the differing package FQN needs no type-mapper
 * configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationRequestApprovedEvent {

    private Long requestId;
    private Long organizationId;
    private String organizationName;
    private String organizationCode;
    private String adminName;
    private String adminEmail;
    private String adminPhone;
    private LocalDateTime approvedAt;
}
